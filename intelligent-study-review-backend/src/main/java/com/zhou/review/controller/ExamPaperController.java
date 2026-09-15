package com.zhou.review.controller;


import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhou.review.agents.progress.SseProgressSink;
import com.zhou.review.annotation.AuthCheck;
import com.zhou.review.common.BaseResponse;
import com.zhou.review.common.ResultUtils;
import com.zhou.review.constant.UserConstant;
import com.zhou.review.exception.BusinessException;
import com.zhou.review.exception.ErrorCode;
import com.zhou.review.model.dto.exam.ExamPaperDto;
import com.zhou.review.model.dto.exam.ExamPaperGradeRequest;
import com.zhou.review.model.dto.exam.ExamPaperQueryRequest;
import com.zhou.review.model.entity.ExamPaper;
import com.zhou.review.model.entity.User;
import com.zhou.review.model.vo.ExamPaperGradeVO;
import com.zhou.review.model.vo.ExamPaperVO;
import com.zhou.review.service.ExamPaperService;
import com.zhou.review.service.UserService;
import com.zhou.review.utils.RedisCacheUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>
 * 试卷主表 前端控制器
 * </p>
 *
 * @author zhou
 * @since 2026-06-23
 */
@RestController
@RequestMapping("/exam-paper")
@Slf4j
public class ExamPaperController {

    /**
     * 流式生成的 SSE 连接超时：多 Agent 循环最坏情况（3 轮生成-审查）留足 10 分钟
     */
    private static final long GENERATE_SSE_TIMEOUT_MS = 10 * 60 * 1000L;

    /**
     * 正在生成中的试卷ID。
     * 防的是重复生成：前端 EventSource 断线会<b>自动重连</b>打同一个 GET 端点，
     * 用户也可能重复点击 —— 不做拦截就会为同一份试卷并发跑多次生成，
     * 既翻倍消耗 token，又会把题目重复写入 question 表。
     * 单实例部署够用；将来多实例需要换成 Redis 锁。
     */
    private final Set<Long> generatingPaperIds = ConcurrentHashMap.newKeySet();

    @Resource
    private ExamPaperService examPaperService;

    @Resource
    private RedisCacheUtil redisCacheUtil;

    @Resource
    private UserService userService;

    @Resource(name = "generationExecutor")
    private ThreadPoolTaskExecutor generationExecutor;
    /**
     * 新增考试试卷
     * @param examPaperDto
     * @return
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<Long> addExamPaper(@RequestBody ExamPaperDto examPaperDto, HttpServletRequest  request) {
        if (examPaperDto == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        ExamPaper examPaper = BeanUtil.copyProperties(examPaperDto, ExamPaper.class);
        Long userId = userService.getLoginUser(request).getId();
        // 补充试卷字段
        examPaper.setUserId(userId);
        examPaper.setGenerationStatus(0);
        examPaper.setIsDelete(0);
        // 试卷名称由 AI 生成，保存时先留空
        if (examPaper.getPaperName() == null) {
            examPaper.setPaperName(examPaper.getSubject() + "试卷");
        }
        // 先将试卷基本信息保存到数据库
        boolean result = examPaperService.save(examPaper);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR);
        }
        // 构建缓存key，删除缓存
        String key = String.format("examPaper:list:%d", userId);
        boolean success = redisCacheUtil.deleteObject(key);
        if (!success) {
            log.warn("删除试卷缓存失败，key: {}", key);
        }
        return ResultUtils.success(examPaper.getId());
    }

    /**
     * 开始生成考试试卷
     * @param taskId
     * @return
     */
    @PostMapping("/generate/{taskId}")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<ExamPaperVO> updateExamPaper(@PathVariable Long taskId) {
        if (taskId == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        ExamPaper examPaper = examPaperService.getById(taskId);
        if (examPaper == null){
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR,"试卷不存在");
        }
        // 生成考试试卷并返回VO对象
        ExamPaperVO examPaperVO = examPaperService.generateExamPaper(examPaper);
        return ResultUtils.success(examPaperVO);
    }

    /**
     * 流式生成考试试卷（SSE）。
     * 与 {updateExamPaper(Long)} 的区别：
     *   立刻返回 SseEmitter，生成挪到专用线程池执行，容器线程被释放 ——
     *   不再有"同步等几十秒到几分钟被网关/浏览器掐断"的风险；
     *  过程中实时推送进度，结束时推 done 或 error。
     * 事件名：stage（进行到哪个 Agent）/ review（每轮审查分数）/
     * token（模型增量，可选）/ done / error / ping（心跳，前端忽略即可）。
     */
    @GetMapping("/generate-stream/{taskId}")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public SseEmitter generateExamPaperStream(@PathVariable Long taskId) {
        if (taskId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        if (examPaperService.getById(taskId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "试卷不存在");
        }
        // 同一份试卷只允许一个生成在跑（断线重连 / 重复点击都会打到这个 GET）
        if (!generatingPaperIds.add(taskId)) {
            return errorEmitter("该试卷正在生成中，请稍候");
        }
        // 建立SSE连接
        SseEmitter emitter = new SseEmitter(GENERATE_SSE_TIMEOUT_MS);
        SseProgressSink sink = new SseProgressSink(emitter);
        // 连接结束/超时/出错都要停掉心跳线程
        emitter.onCompletion(sink::close);
        emitter.onTimeout(sink::close);
        emitter.onError(e -> sink.close());

        try {
            generationExecutor.execute(() -> {
                try {
                    examPaperService.generateExamPaperWithProgress(taskId, sink);
                } catch (Exception e) {
                    log.error("流式生成试卷失败, taskId={}", taskId, e);
                    sink.error(e.getMessage());
                } finally {
                    generatingPaperIds.remove(taskId);
                    // 客户端断开时这里会失败，SseProgressSink 内部已吞掉异常；
                    // 注意：即使断开，生成也已经跑完并落库，用户刷新回来能看到结果
                    emitter.complete();
                }
            });
        } catch (TaskRejectedException e) {
            // 生成任务排队已满：不要静默挂住连接，明确告诉用户
            generatingPaperIds.remove(taskId);
            log.warn("生成线程池已满，拒绝任务 taskId={}", taskId);
            sink.error("当前生成任务较多，请稍后再试");
            sink.close();
            emitter.complete();
        }
        return emitter;
    }

    /**
     * 回一个只带 error 事件的 SSE 连接。
     * 用于"已在进行中"这类前置拒绝 —— 直接抛异常的话前端只会看到连接建立失败，
     * 拿不到真实原因。
     */
    private SseEmitter errorEmitter(String message) {
        SseEmitter emitter = new SseEmitter(GENERATE_SSE_TIMEOUT_MS);
        SseProgressSink sink = new SseProgressSink(emitter);
        sink.error(message);
        sink.close();
        emitter.complete();
        return emitter;
    }

    /**
     * 获取考试试卷列表(用户个人)
     * @param examPaperQueryRequest
     * @return
     */
    @GetMapping("/listPapers")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<Page<ExamPaperVO>> getExamPaperList(ExamPaperQueryRequest examPaperQueryRequest,HttpServletRequest  request) {
        if (examPaperQueryRequest.getPageSize() > 20){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(examPaperService.listPaperPages(examPaperQueryRequest, loginUser.getId()));
    }

    /**
     * 查看试卷详情
     * @param id
     * @return
     */
    @GetMapping("/get/{id}")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<ExamPaperVO> getExamPaper(@PathVariable Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        ExamPaper examPaper = examPaperService.getById(id);
        if (examPaper == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "试卷不存在");
        }
        ExamPaperVO examPaperVO = examPaperService.getPaperDetail(id);
        return ResultUtils.success(examPaperVO);
    }

    /**
     * 删除试卷（逻辑删除）
     * @param id
     * @return
     */
    @PostMapping("/delete/{id}")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<Boolean> deleteExamPaper(@PathVariable Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        ExamPaper examPaper = examPaperService.getById(id);
        if (examPaper == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "试卷不存在");
        }
        examPaperService.deletePaper(id);
        return ResultUtils.success(true);
    }

    /**
     * 批改试卷
     */
    @PostMapping("/grade")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<ExamPaperGradeVO> gradeExamPaper(@RequestBody ExamPaperGradeRequest request) {
        if (request == null || request.getPaperId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        return ResultUtils.success(examPaperService.gradePaper(request.getPaperId(), request.getAnswers()));
    }
}
