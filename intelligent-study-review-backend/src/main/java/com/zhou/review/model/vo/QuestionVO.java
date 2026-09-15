package com.zhou.review.model.vo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @author zhou
 * @since 2026-06-24
 */
@Data
public class QuestionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 题干内容
     */
    private String content;

    /**
     * 题型：SINGLE_CHOICE, MULTIPLE_CHOICE, TRUE_FALSE, SHORT_ANSWER, MATERIAL, ESSAY
     */
    private String questionType;

    /**
     * 分值
     */
    private int score;

    /**
     * 题号
     */
    private int sortOrder;

    /**
     * 选项内容（仅选择题需要，AI 输出为数组）
     */
    private List<String> options;

    /**
     * 材料题的公共阅读材料（仅MATERIAL题型需要）
     */
    private String materialText;

    /**
     * 正确答案或参考答案
     */
    private String answer;

    /**
     * 答案解析
     */
    private String analysis;

    /**
     * 难度：1-简单, 2-中等, 3-困难
     */
    private Integer difficulty;

    /**
     * 关联的知识点标签（AI 输出为数组）
     */
    private List<String> knowledgePoints;

}
