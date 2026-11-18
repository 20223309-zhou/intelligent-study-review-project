package com.zhou.review.model.dto.log;

import com.zhou.review.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 生成日志查询条件（管理员）。
 * 时间用字符串接收而不是 LocalDate：前端传空串 / 带空格 / 格式不对时，
 * 手动解析可以"忽略这个条件继续查"，而不是让整个请求 400。
 *
 * @author zhou
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class GenerationLogQueryRequest extends PageRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 按用户过滤 */
    private Long userId;

    /** 1-成功 2-失败，null 表示全部 */
    private Integer status;

    /** 学科，模糊匹配 */
    private String subject;

    /** 试卷名关键字，模糊匹配 */
    private String keyword;

    /** 开始日期，yyyy-MM-dd（含当天 00:00:00 起） */
    private String startTime;

    /** 结束日期，yyyy-MM-dd（含当天，内部转成次日 00:00:00 开区间） */
    private String endTime;
}
