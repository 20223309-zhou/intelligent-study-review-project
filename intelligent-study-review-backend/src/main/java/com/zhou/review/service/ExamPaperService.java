package com.zhou.review.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhou.review.agents.progress.GenerationProgressSink;
import com.zhou.review.model.dto.exam.ExamPaperQueryRequest;
import com.zhou.review.model.entity.ExamPaper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zhou.review.model.vo.ExamPaperGradeVO;
import com.zhou.review.model.vo.ExamPaperVO;
import java.util.Map;

/**
 * <p>
 * 试卷主表 服务类
 * </p>
 *
 * @author zhou
 * @since 2026-06-23
 */
public interface ExamPaperService extends IService<ExamPaper> {

    /**
     * 生成试卷
     */
    ExamPaperVO generateExamPaper(ExamPaper examPaper);

    /**
     * 带进度的生成试卷（供 SSE 流式端点使用）。
     * 生成过程的阶段变化、每轮审查分数会实时写入 sink；落库完成后额外触发一次 done。
     *
     * @param paperId 试卷ID
     * @param sink    进度回调
     * @return 落库后的试卷 VO
     */
    ExamPaperVO generateExamPaperWithProgress(Long paperId, GenerationProgressSink sink);

    /**
     * 获取试卷列表
     */
    Page<ExamPaperVO> listPaperPages(ExamPaperQueryRequest examPaperQueryRequest, Long id);

    /**
     * 获取试卷详情（含题目）
     */
    ExamPaperVO getPaperDetail(Long id);

    /**
     * 删除试卷（逻辑删除，同时删除关联关系）
     */
    void deletePaper(Long id);

    /**
     * 批改试卷
     */
    ExamPaperGradeVO gradePaper(Long paperId, Map<Integer, String> answers);
}
