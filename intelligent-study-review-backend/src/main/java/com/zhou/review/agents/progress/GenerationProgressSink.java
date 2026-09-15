package com.zhou.review.agents.progress;

import com.zhou.review.model.vo.ExamPaperVO;

/**
 * 试卷生成进度回调。
 * <p>
 * 所有方法都有默认空实现：不关心进度的调用方（如同步生成接口）直接不传或传 {@link #NOOP}。
 * 这样"生成逻辑"与"进度送达方式"解耦 —— 现在只有 SSE 一种实现，
 * 将来要做「刷新页面还能续看进度」，加一个写任务表 + Redis 发布的实现即可，生成侧不用改。
 *
 * @author zhou
 */
public interface GenerationProgressSink {

    /**
     * 空实现。同步生成时使用，避免到处判空。
     */
    GenerationProgressSink NOOP = new GenerationProgressSink() {};

    /**
     * 工作流推进到一个节点（用于展示"现在进行到哪个 Agent"）。
     * 调用方需保证同一节点只推一次：流模式下每个 token 都会产生一个节点事件，
     * 原样推送会把同一件事重复推上千次。
     *
     * @param node  graph 节点名
     * @param agent agent 名，可能为空
     * @param round 当前轮次（从 1 开始）。规划阶段固定为 1
     */
    default void stage(String node, String agent, int round) {
    }

    /**
     * 一轮质量审查结束
     *
     * @param round        第几轮（从 1 开始）
     * @param qualityScore Reviewer 给出的分数
     * @param passed       是否达标（当前规则：>= 8 分退出循环）
     */
    default void review(int round, int qualityScore, boolean passed) {
    }

    /**
     * Writer 的增量输出（token 级，可选）。注意 Writer 产出的是纯 JSON，
     * 前端不宜直接展示，一般只用作"确实在动"的活性指示。
     * 实现方应当自行节流：流模式下这里是"每来一个片段调一次"，一次生成可达成千上万次。
     *
     * @param agent 产出该片段的 agent
     * @param chunk 增量文本
     */
    default void token(String agent, String chunk) {
    }

    /**
     * 生成完成且已落库（此时 paperId 与题目都已写入数据库）
     */
    default void done(ExamPaperVO paper) {
    }

    /**
     * 生成失败
     */
    default void error(String message) {
    }
}
