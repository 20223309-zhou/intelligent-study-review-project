package com.zhou.review.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhou.review.annotation.AuthCheck;
import com.zhou.review.common.BaseResponse;
import com.zhou.review.common.ResultUtils;
import com.zhou.review.constant.UserConstant;
import com.zhou.review.model.dto.log.GenerationLogQueryRequest;
import com.zhou.review.model.vo.GenerationLogStatsVO;
import com.zhou.review.model.vo.GenerationLogVO;
import com.zhou.review.service.GenerationLogService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 生成日志（管理员后台）。
 * <p>
 * 只读接口，没有任何写操作 —— 日志是落库时由生成流程写入的，
 * 不给外部留"伪造日志"的口子。
 * <p>
 * 两个接口都挂 {@code @AuthCheck(mustRole = ADMIN_ROLE)}：
 * 那个切面会先取当前登录用户，未登录抛"未登录"、登录但不是管理员抛"无权限"。
 *
 * @author zhou
 */
@RestController
@RequestMapping("/generation-log")
@Slf4j
public class GenerationLogController {

    @Resource
    private GenerationLogService generationLogService;

    /**
     * 分页查询生成日志。
     * 参数全部可选，不传就是"全部日志按时间倒序"。
     * pageSize 在服务层夹在 1~100，避免前端一次拉一万条。
     */
    @GetMapping("/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<GenerationLogVO>> pageLogs(GenerationLogQueryRequest request) {
        return ResultUtils.success(generationLogService.pageLogs(request));
    }

    /**
     * 近 N 天汇总（卡片 + 趋势图）。
     *
     * @param days 统计窗口，默认 7，服务层夹在 1~90
     */
    @GetMapping("/stats")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<GenerationLogStatsVO> stats(
            @RequestParam(value = "days", required = false, defaultValue = "7") Integer days) {
        return ResultUtils.success(generationLogService.stats(days));
    }
}
