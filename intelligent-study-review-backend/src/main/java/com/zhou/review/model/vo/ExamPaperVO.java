package com.zhou.review.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
public class ExamPaperVO implements Serializable {
    /**
     * 主键
     */
    private Long id;

    /**
     * 试卷名称，如：高等数学期中测试、Java并发编程考核
     */
    private String paperName;

    /**
     * 总分
     */
    private BigDecimal totalScore;

    /**
     * 考试时长（分钟）
     */
    private Integer durationMinutes;

    /**
     * 题卷内容列表
     */
    private List<QuestionVO> questions;
}
