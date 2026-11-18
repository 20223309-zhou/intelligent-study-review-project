package com.zhou.review.agents.progress;

import java.util.Arrays;
import java.util.Locale;

/**
 * 阶段去重闸门：一次生成一个实例。
 * @author zhou
 */
public class StageGate {

    private final String[] wrapperNames;
    private String lastKey;

    /**
     * @param wrapperAgentNames 我们自己给容器（FlowAgent）起的名字，这些节点不推给用户
     */
    public StageGate(String... wrapperAgentNames) {
        this.wrapperNames = wrapperAgentNames == null ? new String[0] : wrapperAgentNames;
    }

    /**
     * @param node  graph 节点名
     * @param agent agent 名，可能为空
     * @param round 当前轮次
     * @return true 表示"这条该推给前端"
     */
    public boolean accept(String node, String agent, int round) {
        if (isWrapper(node) || isWrapper(agent)) {
            return false;
        }
        String label = StageLabels.of(node, agent);
        if (label == null && (agent == null || agent.isBlank())) {
            // 既说不出是哪个 Agent，也不是已知阶段
            return false;
        }
        String display = label != null ? label : StageLabels.key(node, agent);
        String key = display + "|" + round;
        if (key.equals(lastKey)) {
            return false;
        }
        lastKey = key;
        return true;
    }

    private boolean isWrapper(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String n = name.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(wrapperNames)
                .anyMatch(w -> w != null && w.toLowerCase(Locale.ROOT).equals(n));
    }
}
