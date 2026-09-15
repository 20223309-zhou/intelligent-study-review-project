package com.zhou.review.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.time.LocalDateTime;
import java.io.Serializable;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * <p>
 * 题库表
 * </p>
 *
 * @author zhou
 * @since 2026-06-24
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName(value = "question", autoResultMap = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Question implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 题干内容
     */
    private String content;

    /**
     * 题型：SINGLE_CHOICE, MULTIPLE_CHOICE, TRUE_FALSE, SHORT_ANSWER, MATERIAL, ESSAY
     */
    private String questionType;

    /**
     * 选项内容（仅选择题需要）
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> options;

    /**
     * 材料题的公共阅读材料（仅MATERIAL题型需要）
     */
    private String materialText;

    /**
     * 所属试卷ID
     */
    private Long paperId;

    /**
     * 题号
     */
    private Integer sortOrder;

    /**
     * 分值
     */
    private Integer score;

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
     * 关联的知识点标签
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> knowledgePoints;

    private LocalDateTime createTime;

    /**
     * 是否删除
     */
    private Integer isDelete;


}
