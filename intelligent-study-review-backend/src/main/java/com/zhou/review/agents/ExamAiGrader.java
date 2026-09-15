package com.zhou.review.agents;

import cn.hutool.json.JSONUtil;
import com.zhou.review.constant.PromptConstant;
import com.zhou.review.utils.RedisCacheUtil;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 主观题 AI 判分。
 * <p>
 * 刻意**不用** ReactAgent / LoopAgent：判分是"一次调用、结构化返回"，没有循环也没有工具调用，
 * 套上 Agent 框架只会白付模板渲染与消息编排的成本。这里是普通的 ChatModel 调用。
 * <p>
 * 三条硬约束（都是考试场景下必须成立的）：
 * <ul>
 *   <li><b>整卷一次请求</b>：一份卷子可能有十来道主观题，逐题调用会带来 N 倍延迟与 token 开销；</li>
 *   <li><b>同作答同分数</b>：判分结果按 paperId + 题号 + 作答哈希缓存，否则同一份答卷交两次会得到两个分数；</li>
 *   <li><b>失败不判 0 分</b>：AI 或解析失败时返回 pending，由前端提示"待评阅"，
 *       把"没批出来"和"答错了"区分开 —— 写了几百字被判 0 分比不给分更伤信任。</li>
 * </ul>
 *
 * @author zhou
 */
@Slf4j
@Component
public class ExamAiGrader {

    /**
     * 判分缓存有效期（天）。同一份作答在有效期内必须得到同一个分数。
     */
    private static final long CACHE_DAYS = 7L;

    /**
     * 单题作答截断长度。防止个别超长作答把整个批次的 prompt 撑爆。
     */
    private static final int MAX_ANSWER_CHARS = 3000;

    private static final String CACHE_PREFIX = "exam:grade:";

    @Resource
    private OpenAiChatModel openAiChatModel;

    @Resource
    private RedisCacheUtil redisCacheUtil;

    /**
     * 给一批主观题判分。
     *
     * @param paperId 试卷ID（用于缓存隔离）
     * @param items   待判题目（调用方已过滤掉未作答的题）
     * @return sortOrder → 判分结果。每道入参题目都会有对应结果（失败时是 pending）
     */
    public Map<Integer, GradeOutcome> grade(Long paperId, List<GradeItem> items) {
        Map<Integer, GradeOutcome> outcomes = new LinkedHashMap<>();
        if (items == null || items.isEmpty()) {
            return outcomes;
        }

        List<GradeItem> misses = new ArrayList<>();
        for (GradeItem item : items) {
            GradeOutcome cached = readCache(paperId, item);
            if (cached != null) {
                outcomes.put(item.getSortOrder(), cached);
            } else {
                misses.add(item);
            }
        }
        if (misses.isEmpty()) {
            log.info("主观题判分全部命中缓存, paperId={}, 题数={}", paperId, items.size());
            return outcomes;
        }

        try {
            // 调用AI判定主观题，拿到判定结果
            Map<Integer, GradeOutcome> graded = callAi(misses);
            for (GradeItem item : misses) {
                GradeOutcome outcome = graded.get(item.getSortOrder());
                if (outcome == null) {
                    // 模型漏答了这道题：仍要给前端一个明确结论，不能留空
                    outcome = pendingOf(item, "自动评阅未返回该题，请对照参考答案自行核对");
                } else {
                    writeCache(paperId, item, outcome);
                }
                outcomes.put(item.getSortOrder(), outcome);
            }
        } catch (Exception e) {
            log.error("主观题 AI 判分失败, paperId={}, 题数={}", paperId, misses.size(), e);
            for (GradeItem item : misses) {
                outcomes.put(item.getSortOrder(),
                        pendingOf(item, "自动评阅暂时不可用，请对照参考答案自行核对"));
            }
        }
        return outcomes;
    }

    /**
     * 整批一次调用模型，返回解析后的结果。解析不出或调用抛异常时向上抛，由调用方统一降级。
     */
    private Map<Integer, GradeOutcome> callAi(List<GradeItem> items) {
        // 用 JSON 序列化承载学生作答：既保证转义正确，也避免学生答案里的 # / {} 干扰 prompt 结构
        String payload = JSONUtil.toJsonStr(items);
        String prompt = PromptConstant.AI_GRADER_PROMPT + payload;
        log.info("主观题 AI 判分调用：{} 题，prompt {} 字", items.size(), prompt.length());

        String text = openAiChatModel.call(prompt);
        return parse(text, items);
    }

    private Map<Integer, GradeOutcome> parse(String text, List<GradeItem> items) {
        Map<Integer, GradeOutcome> result = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        String json = stripCodeFence(text);
        GradeResponse response;
        try {
            response = JSONUtil.toBean(json, GradeResponse.class);
        } catch (Exception e) {
            log.error("AI 判分结果解析失败，原文：{}", json, e);
            return result;
        }
        if (response == null || response.getResults() == null) {
            return result;
        }

        Map<Integer, GradeItem> itemMap = new HashMap<>();
        for (GradeItem item : items) {
            itemMap.put(item.getSortOrder(), item);
        }

        for (GradeOutcome one : response.getResults()) {
            if (one == null || one.getSortOrder() == null) {
                continue;
            }
            GradeItem item = itemMap.get(one.getSortOrder());
            if (item == null) {
                // 模型编出来的题号，直接丢弃
                continue;
            }
            int full = item.getFullScore() == null ? 0 : item.getFullScore();
            int raw = one.getScore() == null ? 0 : one.getScore();
            // 分数越界一律夹到 [0, 满分]：模型偶尔会给出超满分或负分
            one.setScore(Math.max(0, Math.min(full, raw)));
            one.setPending(false);
            result.put(one.getSortOrder(), one);
        }
        return result;
    }

    /**
     * 模型有时会用 ```json 包裹，去掉围栏再解析。
     */
    private static String stripCodeFence(String text) {
        String s = text.trim();
        if (!s.startsWith("```")) {
            return s;
        }
        int firstLineEnd = s.indexOf('\n');
        if (firstLineEnd > 0) {
            s = s.substring(firstLineEnd + 1);
        }
        int fence = s.lastIndexOf("```");
        if (fence >= 0) {
            s = s.substring(0, fence);
        }
        return s.trim();
    }

    private static GradeOutcome pendingOf(GradeItem item, String comment) {
        return GradeOutcome.builder()
                .sortOrder(item.getSortOrder())
                .score(0)
                .comment(comment)
                .pending(true)
                .build();
    }

    private GradeOutcome readCache(Long paperId, GradeItem item) {
        try {
            GradeOutcome cached = redisCacheUtil.getObject(cacheKey(paperId, item), GradeOutcome.class);
            // 缓存里可能存着上一轮的 pending（AI 当时挂了），那种结果不该被"钉住"，重新判
            if (cached != null && !cached.isPending()) {
                return cached;
            }
        } catch (Exception e) {
            log.warn("判分缓存读取失败，按未命中处理：{}", e.getMessage());
        }
        return null;
    }

    private void writeCache(Long paperId, GradeItem item, GradeOutcome outcome) {
        try {
            redisCacheUtil.setObject(cacheKey(paperId, item), outcome,
                    (int) CACHE_DAYS, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("判分缓存写入失败（不影响本次判分）：{}", e.getMessage());
        }
    }

    /**
     * 缓存键：试卷 + 题号 + 作答内容哈希。作答改了就是新的键，不会复用旧分。
     */
    private String cacheKey(Long paperId, GradeItem item) {
        String answer = item.getStudentAnswer() == null ? "" : item.getStudentAnswer();
        String hash = DigestUtils.md5DigestAsHex(answer.getBytes(StandardCharsets.UTF_8));
        return CACHE_PREFIX + paperId + ":" + item.getSortOrder() + ":" + hash;
    }

    /**
     * 送判的题目。只带判分必需的信息，避免把整卷数据塞进 prompt。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GradeItem {
        private Integer sortOrder;
        private String questionType;
        private String content;
        private String materialText;
        private Integer fullScore;
        private String referenceAnswer;
        private String analysis;
        private String studentAnswer;
    }

    /**
     * 判分结果（也是缓存里存的形态）。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GradeOutcome {
        private Integer sortOrder;
        private Integer score;
        private String comment;
        /**
         * true 表示这次没能判出来（调用失败 / 结果缺失），分数不可信，前端应提示"待评阅"
         */
        private boolean pending;
    }

    /**
     * 模型输出外层结构：{"results":[...]}
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GradeResponse {
        private List<GradeOutcome> results;
    }
}
