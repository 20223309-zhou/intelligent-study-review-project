package com.zhou.review.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 试卷生成日志（一次生成一行，含 token 消耗）。
 * <p>
 * 与 {@code exam_paper.generationStatus} 的区别：那张表只记"当前状态"，
 * 这份日志是流水账 —— 同一份试卷反复重新生成，每次都会留一条，可算成本、算失败率。
 *
 * @author zhou
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("generation_log")
public class GenerationLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 图执行ID（OverAllState 的 _graph_execution_id_），串起这次生成的所有环节 */
    private String traceId;

    private Long userId;

    /** 生成的试卷ID，失败时可能为空 */
    private Long paperId;

    /** 试卷名称：冗余存一份，试卷被改名/删除后仍可追溯 */
    private String paperName;

    private String subject;

    private String gradeOrLevel;

    /** 模型名，如 deepseek-v4.1-flash */
    private String model;

    /** 1-成功 2-失败 */
    private Integer status;

    private String errorMsg;

    private Integer durationMs;

    /** 生成-审查循环轮数，0 表示没进循环 */
    private Integer rounds;

    private Integer qualityScore;

    private Integer llmCalls;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    /** 按 Agent 分摊的 token，JSON 文本：{"planner_agent":123} */
    private String byAgent;

    /** gateway / estimated / none */
    private String usageSource;

    private LocalDateTime createTime;
}
