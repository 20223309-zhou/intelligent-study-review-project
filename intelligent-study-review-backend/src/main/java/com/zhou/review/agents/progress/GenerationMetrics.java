package com.zhou.review.agents.progress;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.Usage;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 一次试卷生成的用量采集器。
 *
 * @author zhou
 */
@Slf4j
public class GenerationMetrics {

    /** 用量来源：网关真实返回 */
    public static final String USAGE_GATEWAY = "gateway";
    /** 用量来源：没有任何数据（网关不支持 / 未开启 stream-usage） */
    public static final String USAGE_NONE = "none";

    private final AtomicInteger promptTokens = new AtomicInteger();
    private final AtomicInteger completionTokens = new AtomicInteger();
    private final AtomicInteger llmCalls = new AtomicInteger();
    private final Map<String, Integer> byAgent = new ConcurrentHashMap<>();

    private volatile String traceId;
    private volatile Integer qualityScore;
    private volatile int rounds;
    private volatile String usageSource = USAGE_NONE;

    /**
     * 累加一次模型调用的用量。
     * 只应在 {@code OutputType.AGENT_MODEL_FINISHED} 时调用：
     * 流式增量事件（AGENT_MODEL_STREAMING）每个 token 一条，累加会把用量放大几十上百倍。
     *
     * @param agent 产出这条输出的 agent 名，可为空
     * @param usage 可能为 null，也可能三个字段全 0（网关没返回），两种情况都直接忽略
     */
    public void addUsage(String agent, Usage usage) {
        if (usage == null) {
            return;
        }
        int prompt = nz(usage.getPromptTokens());
        int completion = nz(usage.getCompletionTokens());
        int total = prompt + completion;
        if (total <= 0) {
            // 全 0 不等于"没消耗"，而是"没数据"：保持 usageSource = none，别把 0 记成真实值
            return;
        }
        llmCalls.incrementAndGet();
        promptTokens.addAndGet(prompt);
        completionTokens.addAndGet(completion);
        usageSource = USAGE_GATEWAY;
        String key = (agent == null || agent.isBlank()) ? "unknown" : agent;
        byAgent.merge(key, total, Integer::sum);
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    public int getPromptTokens() {
        return promptTokens.get();
    }

    public int getCompletionTokens() {
        return completionTokens.get();
    }

    public int getTotalTokens() {
        return promptTokens.get() + completionTokens.get();
    }

    public int getLlmCalls() {
        return llmCalls.get();
    }

    /** 按 agent 分摊的总 token，顺序稳定（便于日志阅读） */
    public Map<String, Integer> getByAgent() {
        return new LinkedHashMap<>(byAgent);
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public Integer getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(Integer qualityScore) {
        this.qualityScore = qualityScore;
    }

    public int getRounds() {
        return rounds;
    }

    public void setRounds(int rounds) {
        this.rounds = rounds;
    }

    public String getUsageSource() {
        return usageSource;
    }

    @Override
    public String toString() {
        return "GenerationMetrics{traceId=" + traceId + ", rounds=" + rounds
                + ", llmCalls=" + llmCalls.get()
                + ", prompt=" + getPromptTokens() + ", completion=" + getCompletionTokens()
                + ", usageSource=" + usageSource + '}';
    }
}
