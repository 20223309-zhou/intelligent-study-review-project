package com.zhou.review.agents.progress;

import com.zhou.review.model.vo.ExamPaperVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 把生成进度写成 SSE 事件推给前端。
 * 事件名与前端约定：stage / review / token / done / error / ping。
 * 推送失败（客户端断开、emitter 已完成）只记日志不抛异常 ——
 * 生成应继续跑完并落库，用户刷新回来仍然能看到结果。
 *
 * @author zhou
 */
@Slf4j
public class SseProgressSink implements GenerationProgressSink {

    /**
     * token 事件最小发送间隔。流模式下 Review/Writer 每产生一个字符就是一个事件，
     * 原样透传会产生上千条 SSE 帧；前端只需要知道"确实在动"，故在此节流。
     */
    private static final long TOKEN_THROTTLE_MS = 200L;

    private final SseEmitter emitter;
    private final ScheduledExecutorService pinger;

    /**
     * 已接收的模型输出字符数，用于前端展示"输出中 · N 字符"的活性指示
     */
    private final AtomicInteger receivedChars = new AtomicInteger();
    private final AtomicLong lastTokenSentAt = new AtomicLong(0L);

    public SseProgressSink(SseEmitter emitter) {
        this.emitter = emitter;
        // 心跳：大模型调用期间可能几十秒没有任何事件，中间层（Nginx 等）会把空闲连接掐掉
        this.pinger = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "exam-gen-sse-ping");
            t.setDaemon(true);
            return t;
        });
        this.pinger.scheduleAtFixedRate(this::ping, 15, 15, TimeUnit.SECONDS);
    }

    @Override
    public void stage(String node, String agent, int round) {
        String key = agent != null && !agent.isBlank() ? agent : node;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("node", node == null ? "" : node);
        data.put("agent", agent == null ? "" : agent);
        data.put("round", round);
        data.put("label", resolveLabel(key));
        send("stage", data);
    }

    /**
     * 把节点名翻译成给用户看的中文。用 contains 而非全等匹配：
     * 框架实际给出的节点名带层级前缀（实测为 subgraph_planner_agent / subgraph_reviewer_agent），
     * 全等匹配会全部落空并把英文名直接甩到界面上。
     * 匹配不到则原样返回，保证新增/重命名 Agent 时进度不会空白。
     */
    private static String resolveLabel(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        String k = key.toLowerCase(Locale.ROOT);
        if (k.contains("planner")) {
            return "正在规划试卷结构";
        }
        if (k.contains("writer")) {
            return "正在生成题目";
        }
        if (k.contains("review")) {
            return "正在做质量审查";
        }
        return key;
    }

    @Override
    public void review(int round, int qualityScore, boolean passed) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("round", round);
        data.put("qualityScore", qualityScore);
        data.put("passed", passed);
        send("review", data);
    }

    @Override
    public void token(String agent, String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        int total = receivedChars.addAndGet(chunk.length());

        long now = System.currentTimeMillis();
        long last = lastTokenSentAt.get();
        if (now - last < TOKEN_THROTTLE_MS) {
            return;
        }
        // 并发下只让一个线程发，其余直接跳过（节流本身允许丢帧）
        if (!lastTokenSentAt.compareAndSet(last, now)) {
            return;
        }

        // 前端不展示 Writer 的原文（那是纯 JSON），只用来证明"确实在输出"
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("agent", agent == null ? "" : agent);
        data.put("chars", total);
        send("token", data);
    }

    @Override
    public void done(ExamPaperVO paper) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (paper != null) {
            data.put("paperName", paper.getPaperName());
            data.put("totalScore", paper.getTotalScore());
            data.put("questionCount", paper.getQuestions() == null ? 0 : paper.getQuestions().size());
        }
        send("done", data);
    }

    @Override
    public void error(String message) {
        send("error", Map.of("message", message == null || message.isBlank() ? "生成失败" : message));
    }

    /**
     * 停止心跳。由调用方在 emitter 结束/超时后调用。
     */
    public void close() {
        pinger.shutdownNow();
    }

    private void ping() {
        send("ping", Map.of("t", System.currentTimeMillis()));
    }

    private void send(String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (Exception e) {
            // 这里是"尽力而为"的通知：客户端断开、emitter 已完成、序列化失败……
            // 一律吞掉。若让异常逃出去，Reactor 会取消整条流，生成反而被中止。
            log.debug("SSE 推送失败（事件 {}）：{}", event, e.getMessage());
        }
    }
}
