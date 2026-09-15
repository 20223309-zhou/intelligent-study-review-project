package com.zhou.review.model.dto.exam;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExamPaperDto {

    /**
     * 创建人ID
     */
    private Long userId;
    /**
     * 题型要求
     * key: 题型名称，value: 题型数量
     * 例如：
     * {"选择题":1,"判断题":2,"单选题":3}
     */
    private Map<String, Integer> questionConfig;

    /**
     * 试卷描述备注
     */
    private String comment;

    /**
     * 试卷名称
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
     * 科目
     */
    private String subject;

    /**
     * 年级或等级
     */
    private String gradeOrLevel;

    /**
     * 教材版本
     */
    private String textbookVersion;

    /**
     * 章节范围
     */
    private String chapterRange;
}
