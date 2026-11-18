package com.zhou.review.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhou.review.agents.progress.GenerationMetrics;
import com.zhou.review.model.dto.log.GenerationLogQueryRequest;
import com.zhou.review.model.entity.ExamPaper;
import com.zhou.review.model.vo.ExamPaperVO;
import com.zhou.review.model.vo.GenerationLogStatsVO;
import com.zhou.review.model.vo.GenerationLogVO;

/**
 * 生成日志。一次"生成试卷"落一行，成功失败都记。
 *
 * @author zhou
 */
public interface GenerationLogService {

    /**
     * 记录一次成功的生成（试卷与题目已入库后调用）。
     *
     * @param paper      本次请求的试卷参数（取名称/学科/用户）
     * @param saved      落库后的试卷，用于取 paperId 与最终试卷名，可为 null
     * @param metrics    生成过程中采集的用量
     * @param durationMs 总耗时（毫秒）
     */
    void recordSuccess(ExamPaper paper, ExamPaperVO saved, GenerationMetrics metrics, long durationMs);

    /**
     * 记录一次失败的生成。
     */
    void recordFailure(ExamPaper paper, GenerationMetrics metrics, long durationMs, Throwable error);

    /**
     * 管理员分页查询生成日志。
     */
    Page<GenerationLogVO> pageLogs(GenerationLogQueryRequest request);

    /**
     * 近 N 天的汇总统计，用于管理员看板。
     */
    GenerationLogStatsVO stats(Integer days);
}
