package com.zhou.review.model.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 生成日志的汇总统计（管理员看板顶部卡片 + 趋势图）。
 *
 * @author zhou
 */
@Data
@Builder
public class GenerationLogStatsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 统计窗口（天） */
    private Integer days;

    /** 总生成次数 */
    private Long totalCount;

    private Long successCount;

    private Long failCount;

    /** 成功率，百分比，保留 1 位小数（如 95.5） */
    private Double successRate;

    /** token 合计 */
    private Long totalTokens;

    /** 单次平均 token */
    private Long avgTokens;

    /** 平均耗时（毫秒） */
    private Long avgDurationMs;

    /** 平均循环轮数 */
    private Double avgRounds;

    /** 按天趋势，缺失的日期补 0，前端可直接画图 */
    private List<Daily> daily;

    @Data
    @Builder
    public static class Daily implements Serializable {

        private static final long serialVersionUID = 1L;

        /** yyyy-MM-dd */
        private String date;

        private Long count;

        private Long failCount;

        private Long tokens;
    }
}
