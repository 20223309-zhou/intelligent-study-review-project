package com.zhou.review.model.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 生成日志展示对象（管理员后台用）。
 *
 * @author zhou
 */
@Data
@Builder
public class GenerationLogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String traceId;

    private Long userId;

    /** 账号，便于管理员辨认（由 userId 批量补齐） */
    private String userAccount;

    private String userName;

    private Long paperId;

    private String paperName;

    private String subject;

    private String gradeOrLevel;

    private String model;

    /** 1-成功 2-失败 */
    private Integer status;

    private String errorMsg;

    private Integer durationMs;

    private Integer rounds;

    private Integer qualityScore;

    private Integer llmCalls;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    /** 按 Agent 分摊的 token，库里存 JSON 文本，出参解析成对象方便前端直接遍历 */
    private Map<String, Integer> byAgent;

    /** gateway / estimated / none */
    private String usageSource;

    private LocalDateTime createTime;
}
