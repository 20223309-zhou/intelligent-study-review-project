package com.zhou.review.model.entity;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;
import java.io.Serializable;
import java.util.Map;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * <p>
 * 试卷主表
 * </p>
 *
 * @author zhou
 * @since 2026-06-23
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName(value = "exam_paper",autoResultMap = true)
public class ExamPaper implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 试卷唯一ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    /**
     * 创建人ID
     */
    private Long userId;

    /**
     * 试卷描述备注
     */
    private String comment;

    /**
     * 试卷名称，如：高等数学期中测试、Java并发编程考核
     */
    private String paperName;

    /**
     * 题型要求
     * key: 题型名称，value: 题型数量
     * 例如：
     * {"选择题":1,"判断题":2,"单选题":3}
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Integer> questionConfig;

    /**
     * 学科/专业，如：高等数学、计算机科学、临床医学
     */
    private String subject;

    /**
     * 年级或专业层次，如：小学、初中、大学
     */
    private String gradeOrLevel;

    /**
     * 教材版本，如：人教版、同济版、高等教育出版社
     */
    private String textbookVersion;

    /**
     * 考察章节范围
     */
    private String chapterRange;

    /**
     * 生成状态：0-生成中, 1-已生成, 2-审查驳回, 3-生成失败
     */
    private Integer generationStatus;

    /**
     * AI审查意见
     */
    private String aiReviewComment;

    /**
     * 存储用户输入的表单数据和AI的特殊要求
     */
    private String promptContext;

    /**
     * 总分
     */
    private BigDecimal totalScore;

    /**
     * 考试时长（分钟）
     */
    private Integer durationMinutes;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /**
     * 是否删除
     */
    private Integer isDelete;

}
