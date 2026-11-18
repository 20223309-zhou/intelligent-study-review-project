package com.zhou.review.agents;

import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LoopAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.loop.ConditionLoopStrategy;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.zhou.review.agents.progress.GenerationMetrics;
import com.zhou.review.agents.progress.GenerationProgressSink;
import com.zhou.review.agents.progress.StageGate;
import com.zhou.review.constant.PromptConstant;
import com.zhou.review.exception.BusinessException;
import com.zhou.review.exception.ErrorCode;
import com.zhou.review.model.entity.ExamPaper;
import com.zhou.review.model.vo.ExamPaperVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.template.TemplateRenderer;
import org.springframework.ai.template.ValidationMode;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

@Component
@Slf4j
public class AsyncGenExamPaper {

    /**
     * 质量合格线：Reviewer 打分 >= 该值即退出生成-审查循环
     */
    private static final int QUALITY_PASS_SCORE = 8;

    /**
     * 我们自己给「容器」起的名字。框架会把 FlowAgent 编译成子图，子图的入口/派发/出口节点
     * 也会产出 NodeOutput，agent 字段就是这些名字 —— 它们不是"某个 Agent 在干活"，
     * 推给用户只会让进度里混进 work flowAgent 这种没意义的行。命名集中在这里，
     * 建图与过滤共用同一份常量，改名不会漏。
     */
    private static final String WORKFLOW_AGENT = "workflowAgent";
    private static final String LOOP_AGENT = "exam_paper_agent";
    private static final String WRITE_REVIEW_AGENT = "write_review_agent";

    @Resource
    private OpenAiChatModel openAiChatModel;

    /**
     * 同步生成试卷（保持原有行为：一次 invoke，不产生进度事件）
     *
     * @param examPaper
     * @return
     */
    public ExamPaperVO generateExamPaper(ExamPaper examPaper) {
        return generateExamPaper(examPaper, GenerationProgressSink.NOOP, new GenerationMetrics());
    }

    /**
     * 流式生成试卷：与同步版本共用同一套 Agent 编排，只把 invoke 换成 stream，
     * 把工作流每个节点的完成情况、每轮审查分数通过 sink 推出去。
     *
     * @param examPaper 试卷基本信息
     * @param sink      进度回调，可为 null（等价于同步）
     * @return 反序列化后的试卷 VO；拿不到时返回 null
     */
    public ExamPaperVO generateExamPaper(ExamPaper examPaper, GenerationProgressSink sink) {
        return generateExamPaper(examPaper, sink, new GenerationMetrics());
    }

    /**
     * 带用量统计的生成。metrics 由调用方创建（每次生成一个，不能共享），
     * 生成结束后从这里读 token / 轮数 / traceId 落日志。
     */
    public ExamPaperVO generateExamPaper(ExamPaper examPaper, GenerationProgressSink sink,
                                         GenerationMetrics metrics) {
        return run(examPaper, sink == null ? GenerationProgressSink.NOOP : sink, metrics);
    }

    private ExamPaperVO run(ExamPaper examPaper, GenerationProgressSink sink, GenerationMetrics metrics) {
        SequentialAgent workflowAgent = buildWorkflowAgent();
        Map<String, Object> inputMap = buildInput(examPaper);

        Optional<OverAllState> result;
        try {
            // 统一走 stream。原来 sink == null 时走 invoke，但 invoke 只回 OverAllState，
            // 而 state 里没有 token 用量（实测只有 input/messages/各 outputKey），
            // 那样同步接口就永远统计不到消耗。sink 传 NOOP 时对调用方完全无感，
            // 只是底层多消费若干个事件。
            result = runStream(workflowAgent, inputMap, sink, metrics);
        } catch (GraphRunnerException e) {
            log.error("试卷生成失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "试卷生成失败");
        }

        return result.map(this::extractPaper).orElse(null);
    }

    /**
     * 流式执行：逐节点推送进度。
     * 节点名与 agent 名由框架给出，这里不做硬编码匹配 —— 匹配不到就原样推给前端，
     * 保证"新增/重命名 Agent"时进度不会整体失效（只是标签退化成英文名）。
     * 关键：流模式下 token 级事件会成千上万地来（实测一次生成里 reviewer 节点的事件数达数百），
     * 因此 stage 按"节点是否变化"去重、review 按"审查报告是否变化"去重，
     * 否则同一件事会被重复推送成百上千次（表现为"轮数一直狂飙"）。
     */
    private Optional<OverAllState> runStream(SequentialAgent workflowAgent,
                                             Map<String, Object> inputMap,
                                             GenerationProgressSink sink,
                                             GenerationMetrics metrics) throws GraphRunnerException {
        AtomicReference<OverAllState> lastState = new AtomicReference<>();
        AtomicInteger reviewRound = new AtomicInteger(0);
        StageGate stageGate = new StageGate(WORKFLOW_AGENT, LOOP_AGENT, WRITE_REVIEW_AGENT);
        AtomicReference<String> lastReviewText = new AtomicReference<>();

        workflowAgent.stream(inputMap)
                .doOnNext(output -> {
                    OverAllState state = output.state();
                    if (state != null) {
                        lastState.set(state);
                    }

                    // —— 阶段：同一「语义阶段」在同一轮内只推第一次 ——
                    // 具体规则见 StageGate：按展示文案去重（不是 node|agent），
                    // 并丢掉没有 agent 的图包装节点。
                    int stageRound = reviewRound.get() + 1;
                    if (stageGate.accept(output.node(), output.agent(), stageRound)) {
                        sink.stage(output.node(), output.agent(), stageRound);
                    }

                    // —— token 级增量：只作活性指示，由 sink 自行节流 ——
                    if (output instanceof StreamingOutput<?> streamingOutput
                            && streamingOutput.getOutputType() == OutputType.AGENT_MODEL_STREAMING) {
                        sink.token(streamingOutput.agent(), streamingOutput.chunk());
                    }

                    // —— 用量：只在「模型完成」事件上取 ——
                    // 流式增量事件每个 token 一条且 usage 是空壳，累加会放大几十倍；
                    // 网关没开 stream-usage 时 usage 全 0，addUsage 内部会忽略并保持 usageSource=none。
                    if (output instanceof StreamingOutput<?> finished
                            && finished.getOutputType() == OutputType.AGENT_MODEL_FINISHED) {
                        metrics.addUsage(finished.agent(), output.tokenUsage());
                    }

                    // —— 审查轮次：同一份审查报告只计一次 ——
                    // 循环里第二轮 reviewer 开始跑时，state 里的 reviewComment 还是上一轮的，
                    // 逐个 token 事件去读会反复解析出同一个分数，这就是"轮数狂飙"的直接原因。
                    if (isReviewerNode(output.node(), output.agent())) {
                        String reviewText = textOf(
                                state == null ? null : state.value("reviewComment").orElse(null));
                        Integer score = parseQualityScore(reviewText);
                        if (score != null && reviewText != null && !reviewText.isBlank()
                                && !reviewText.equals(lastReviewText.getAndSet(reviewText))) {
                            sink.review(reviewRound.incrementAndGet(), score, score >= QUALITY_PASS_SCORE);
                            metrics.setQualityScore(score);
                        }
                    }
                })
                .doOnError(e -> {
                    log.error("流式生成失败", e);
                    sink.error(e.getMessage());
                })
                .blockLast();

        OverAllState finalState = lastState.get();
        if (finalState != null) {
            // 图执行ID：把一次生成的所有环节串起来的 trace id，落日志用
            finalState.value("_graph_execution_id_")
                    .ifPresent(id -> metrics.setTraceId(String.valueOf(id)));
        }
        metrics.setRounds(reviewRound.get());

        return Optional.ofNullable(finalState);
    }

    /**
     * 判断是不是审查节点。用宽松匹配，避免框架节点命名变化后进度整体失效。
     */
    private static boolean isReviewerNode(String node, String agent) {
        String key = agent != null && !agent.isBlank() ? agent : node;
        return key != null && key.toLowerCase().contains("review");
    }

    /**
     * 从 state 中取出 Writer 产出的试卷 JSON 并反序列化
     */
    private ExamPaperVO extractPaper(OverAllState state) {
        AtomicReference<ExamPaperVO> resultRef = new AtomicReference<>();
        state.value("generatedPaper").ifPresent(paper -> {
            if (paper instanceof AssistantMessage) {
                String jsonStr = ((AssistantMessage) paper).getText();
                resultRef.set(JSONUtil.toBean(jsonStr, ExamPaperVO.class));
            }
        });
        return resultRef.get();
    }

    /**
     * 构建工作流：规划 -> （生成 -> 审查）循环
     */
    private SequentialAgent buildWorkflowAgent() {
        // 使用 # 作为模板定界符，避免与 JSON 的 {} 冲突
        TemplateRenderer templateRenderer = StTemplateRenderer.builder()
                .startDelimiterToken('#')
                .endDelimiterToken('#')
                .validationMode(ValidationMode.NONE)
                .build();

        // 创建资料收集与大纲规划Agent
        ReactAgent plannerAgent = ReactAgent.builder()
                .name("planner_agent")
                .model(openAiChatModel)
                .description("资料收集与大纲规划Agent ")
                .instruction(PromptConstant.PLANNER_PROMPT_AGENT1)
                .templateRenderer(templateRenderer)
                .outputKey("examPlan")
                .build();

        // 创建试卷生成Agent
        ReactAgent writerAgent = ReactAgent.builder()
                .name("writer_agent")
                .model(openAiChatModel)
                .description("试卷生成Agent")
                .instruction(PromptConstant.GENERATOR_PROMPT_AGENT2)
                .templateRenderer(templateRenderer)
                .outputKey("generatedPaper")
                .build();

        // 创建质量审查Agent
        ReactAgent reviewerAgent = ReactAgent.builder()
                .name("reviewer_agent")
                .model(openAiChatModel)
                .description("质量审查与格式化 Agent")
                .instruction(PromptConstant.REVIEWER_PROMPT_AGENT3)
                .templateRenderer(templateRenderer)
                .outputKey("reviewComment")
                .build();

        // 创建试卷生成与审查的顺序执行子Agent
        SequentialAgent writeReviewAgent = SequentialAgent.builder()
                .name(WRITE_REVIEW_AGENT)
                .description("试卷生成与审查Agent")
                .subAgents(List.of(writerAgent, reviewerAgent))
                .build();

        // 创建试卷生成审查循环agent
        LoopAgent loopAgent = LoopAgent.builder()
                .name(LOOP_AGENT)
                .description("试卷生成审查循环Agent")
                .subAgent(writeReviewAgent)
                .loopStrategy(getConditionLoopStrategy())
                .build();

        // 创建完整的工作流 Agent：计划 -> （生成 -> 审查）循环
        return SequentialAgent.builder()
                .name(WORKFLOW_AGENT)
                .description("试卷生成工作流，计划->生成->审查")
                .subAgents(List.of(plannerAgent, loopAgent))
                .build();
    }

    /**
     * 构建工作流入参
     */
    private Map<String, Object> buildInput(ExamPaper examPaper) {
        Map<String, Object> inputMap = new HashMap<>();
        inputMap.put("subject", nullToEmpty(examPaper.getSubject()));
        inputMap.put("gradeOrLevel", nullToEmpty(examPaper.getGradeOrLevel()));
        inputMap.put("textbookVersion", nullToEmpty(examPaper.getTextbookVersion()));
        inputMap.put("durationMinutes", examPaper.getDurationMinutes());
        inputMap.put("totalScore", examPaper.getTotalScore().doubleValue());
        inputMap.put("questionConfig", JSONUtil.toJsonStr(examPaper.getQuestionConfig()));
        inputMap.put("comment", nullToEmpty(examPaper.getComment()));
        // 预置后续 Agent 的输出变量为空串，避免首轮渲染时属性未定义报错
        inputMap.put("generatedPaper", "");
        inputMap.put("reviewComment", "");
        return inputMap;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String textOf(Object value) {
        if (value instanceof AssistantMessage assistantMessage) {
            return assistantMessage.getText();
        }
        return value == null ? null : value.toString();
    }

    /**
     * 从审查报告 JSON 里取 qualityScore。解析不出来返回 null（视为"格式不符合要求，继续循环"）。
     * 退出条件与进度推送共用这一份解析逻辑。
     */
    private static Integer parseQualityScore(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> reviewMap = JSONUtil.toBean(content, Map.class);
            Object scoreObj = reviewMap.get("qualityScore");
            if (scoreObj instanceof Number number) {
                return number.intValue();
            }
        } catch (Exception e) {
            log.debug("审查报告解析失败，按未达标处理：{}", e.getMessage());
        }
        return null;
    }

    @NotNull
    private static ConditionLoopStrategy getConditionLoopStrategy() {
        Predicate<List<Message>> myExitCondition = messages -> {
            // 从消息列表中找到最后一条消息（通常是 Agent 3 的审查结果）
            if (messages == null || messages.isEmpty()) {
                return false;
            }
            Message lastMessage = messages.get(messages.size() - 1);
            Integer score = parseQualityScore(lastMessage.getText());
            // 分数 >= 8 时返回 true，触发退出循环；解析失败按 false 处理，让它重试
            return score != null && score >= QUALITY_PASS_SCORE;
        };

        return new ConditionLoopStrategy(myExitCondition) {
            private int loopCount = 0;

            @Override
            public int maxLoopCount() {
                return 3;
            }

            @Override
            public Map<String, Object> loopDispatch(OverAllState state) {
                loopCount++;
                log.info("========== 第 {} 次生成-审查循环 ==========", loopCount);
                return super.loopDispatch(state);
            }
        };
    }
}
