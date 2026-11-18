package com.zhou.review.agents.progress;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阶段去重的回归测试。
 * <p>
 * 输入取自线上真实出现过的事件序列（先是从 19 步里一大半重复，然后还剩 4 条 workflowAgent），
 * 钉住三条规则：容器节点不推、同一文案同轮只推一次、未识别但带 agent 名的仍推。
 * <p>
 * 纯逻辑测试：不起 Spring、不调模型。
 */
class StageGateTest {

    /** 我们给容器起的名字，与 AsyncGenExamPaper 里的常量一致 */
    private static final String WORKFLOW = "workflowAgent";

    private static StageGate gate() {
        return new StageGate(WORKFLOW, "exam_paper_agent", "write_review_agent");
    }

    /** 真实节点的命名形态：同一个 agent 会先后以包装节点 / agent 节点 / 空 agent 的形式出现 */
    private record Ev(String node, String agent) {
    }

    @Test
    void 同一阶段的多种节点命名只推一次() {
        StageGate gate = gate();

        List<Ev> plannerEvents = List.of(
                new Ev("subgraph_planner_agent", "planner_agent"),
                new Ev("planner_agent", "planner_agent"),
                new Ev("planner_agent", ""));

        long pushed = plannerEvents.stream().filter(e -> gate.accept(e.node(), e.agent(), 1)).count();

        assertEquals(1, pushed, "同一个阶段（正在规划试卷结构）应只推一次");
    }

    @Test
    void 容器节点不推_按名字精确匹配() {
        StageGate gate = gate();
        // 实测这类事件的 agent 字段就是容器名，不是空串 —— 上一版按"agent 为空"判断才漏掉的
        assertFalse(gate.accept("workflowAgent", WORKFLOW, 1));
        assertFalse(gate.accept(WORKFLOW, WORKFLOW, 2));
        assertFalse(gate.accept("exam_paper_agent", "exam_paper_agent", 1));
        assertFalse(gate.accept("write_review_agent", "", 1));
    }

    @Test
    void 起始结束节点不推() {
        StageGate gate = gate();
        assertFalse(gate.accept("__START__", "", 1));
        assertFalse(gate.accept("__END__", "", 1));
    }

    @Test
    void 未识别但带agent名的节点仍然推送() {
        StageGate gate = gate();
        // 兜底：以后新增了 formatter_agent 之类，没有中文标签也要能看到
        assertTrue(gate.accept("subgraph_formatter_agent", "formatter_agent", 1));
    }

    @Test
    void 下一轮的同一阶段会再次推送() {
        StageGate gate = gate();
        assertTrue(gate.accept("writer_agent", "writer_agent", 1));
        assertTrue(gate.accept("writer_agent", "writer_agent", 2), "轮次变了要重推");
        assertFalse(gate.accept("writer_agent", "writer_agent", 2), "同轮同阶段不重复");
    }

    @Test
    void 真实序列_11步里只留5条阶段() {
        StageGate gate = gate();
        // 复刻截图顺序：两轮 + 4 条 workflowAgent 容器事件
        List<Ev> stageEvents = List.of(
                new Ev(WORKFLOW, WORKFLOW),                       // 0:00 容器
                new Ev("subgraph_planner_agent", "planner_agent"), // 0:18 规划 ✓
                new Ev("planner_agent", ""),                      // 0:22 容器的另一种形态
                new Ev("subgraph_writer_agent", "writer_agent"),  // 0:26 生成 ✓
                new Ev("reviewer_agent", "reviewer_agent"),       // 1:08 审查 ✓
                new Ev(WORKFLOW, WORKFLOW),                       // 1:17 第 2 轮开始
                new Ev("subgraph_writer_agent", "writer_agent"),  // 1:21 生成（第 2 轮）✓
                new Ev("reviewer_agent", "reviewer_agent"),       // 1:50 审查（第 2 轮）✓
                new Ev(WORKFLOW, WORKFLOW));                      // 2:03 第 3 轮派发（其实已通过，要退出）

        int pushed = 0;
        // 前 5 条属于第 1 轮，后 4 条属于第 2/3 轮
        for (int i = 0; i < stageEvents.size(); i++) {
            Ev e = stageEvents.get(i);
            int round = i < 5 ? 1 : 2;
            if (gate.accept(e.node(), e.agent(), round)) {
                pushed++;
            }
        }

        assertEquals(5, pushed, "11 步里真正有信息量的只有两轮各 规划/生成/审查，容器事件全丢");
    }
}
