package com.zhou.review.agents.progress;

import java.util.Locale;

/**
 * 阶段标签：把框架给的 (node, agent) 翻译成给用户看的中文。
 * @author zhou
 */
public final class StageLabels {

    private StageLabels() {
    }

    /**
     * 判断"是哪个 agent 在干活"：agent 优先，为空退化成 node。
     */
    public static String key(String node, String agent) {
        if (agent != null && !agent.isBlank()) {
            return agent;
        }
        return node == null ? "" : node;
    }

    /**
     * 已知阶段返回中文标签；<b>没匹配到返回 null</b>。
     * 返回 null 而不是原样返回 key，是为了让调用方能区分"这是个业务阶段"和
     * "这只是图的包装节点（workflowAgent / __START__ 等）"——后者对用户没有信息量。
     *
     * @return 中文标签，或 null 表示未识别
     */
    public static String of(String node, String agent) {
        String k = key(node, agent).toLowerCase(Locale.ROOT);
        if (k.contains("planner")) {
            return "正在规划试卷结构";
        }
        if (k.contains("writer")) {
            return "正在生成题目";
        }
        if (k.contains("review")) {
            return "正在做质量审查";
        }
        return null;
    }
}
