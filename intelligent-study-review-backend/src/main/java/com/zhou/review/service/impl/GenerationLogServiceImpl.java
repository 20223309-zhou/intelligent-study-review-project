package com.zhou.review.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhou.review.agents.progress.GenerationMetrics;
import com.zhou.review.mapper.GenerationLogMapper;
import com.zhou.review.mapper.UserMapper;
import com.zhou.review.model.dto.log.GenerationLogQueryRequest;
import com.zhou.review.model.entity.ExamPaper;
import com.zhou.review.model.entity.GenerationLog;
import com.zhou.review.model.entity.User;
import com.zhou.review.model.vo.ExamPaperVO;
import com.zhou.review.model.vo.GenerationLogStatsVO;
import com.zhou.review.model.vo.GenerationLogVO;
import com.zhou.review.service.GenerationLogService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 生成日志落库。
 * 两条硬要求：
 * 1. 不能影响主流程 —— 写日志失败只 warn，绝不把已经生成好的试卷搞失败；
 * 2. 失败也要留下痕迹 —— 否则算不出失败率，也发现不了"某类配置必然失败"。
 *
 * @author zhou
 */
@Service
@Slf4j
public class GenerationLogServiceImpl extends ServiceImpl<GenerationLogMapper, GenerationLog>
        implements GenerationLogService {

    /** 错误信息只留一行，别把整段堆栈塞进 varchar(1024) */
    private static final int MAX_ERROR_LEN = 1000;

    @Value("${spring.ai.openai.chat.options.model:unknown}")
    private String model;

    @Resource
    private UserMapper userMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordSuccess(ExamPaper paper, ExamPaperVO saved, GenerationMetrics metrics, long durationMs) {
        try {
            String paperName = saved != null && saved.getPaperName() != null
                    ? saved.getPaperName()
                    : paper.getPaperName();
            save(build(paper, saved == null ? null : saved.getId(), paperName, metrics,
                    durationMs, 1, null));
        } catch (Exception e) {
            log.warn("写生成日志失败（不影响生成结果）：{}", e.getMessage(), e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordFailure(ExamPaper paper, GenerationMetrics metrics, long durationMs, Throwable error) {
        try {
            save(build(paper, null, paper.getPaperName(), metrics, durationMs, 2, error));
        } catch (Exception e) {
            log.warn("写生成失败日志失败：{}", e.getMessage(), e);
        }
    }

    private GenerationLog build(ExamPaper paper, Long paperId, String paperName,
                                GenerationMetrics metrics, long durationMs,
                                int status, Throwable error) {
        GenerationLog logEntity = GenerationLog.builder()
                .traceId(metrics == null ? null : metrics.getTraceId())
                .userId(paper.getUserId())
                .paperId(paperId)
                .paperName(cut(paperName, 500))
                .subject(cut(paper.getSubject(), 250))
                .gradeOrLevel(cut(paper.getGradeOrLevel(), 120))
                .model(model)
                .status(status)
                .durationMs((int) Math.min(durationMs, Integer.MAX_VALUE))
                .build();

        if (metrics != null) {
            logEntity.setRounds(metrics.getRounds())
                    .setQualityScore(metrics.getQualityScore())
                    .setLlmCalls(metrics.getLlmCalls())
                    .setPromptTokens(metrics.getPromptTokens())
                    .setCompletionTokens(metrics.getCompletionTokens())
                    .setTotalTokens(metrics.getTotalTokens())
                    .setUsageSource(metrics.getUsageSource())
                    .setByAgent(metrics.getByAgent().isEmpty() ? null : JSONUtil.toJsonStr(metrics.getByAgent()));
        }

        if (error != null) {
            logEntity.setErrorMsg(cut(error.getClass().getSimpleName() + ": " + error.getMessage(), MAX_ERROR_LEN));
        }
        return logEntity;
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    // ==================== 管理员查询 ====================

    @Override
    public Page<GenerationLogVO> pageLogs(GenerationLogQueryRequest request) {
        int pageNum = Math.max(1, request.getPageNum());
        int pageSize = Math.min(100, Math.max(1, request.getPageSize()));

        LambdaQueryWrapper<GenerationLog> qw = new LambdaQueryWrapper<>();
        if (request.getUserId() != null) {
            qw.eq(GenerationLog::getUserId, request.getUserId());
        }
        if (request.getStatus() != null) {
            qw.eq(GenerationLog::getStatus, request.getStatus());
        }
        if (StringUtils.hasText(request.getSubject())) {
            qw.like(GenerationLog::getSubject, request.getSubject().trim());
        }
        if (StringUtils.hasText(request.getKeyword())) {
            qw.like(GenerationLog::getPaperName, request.getKeyword().trim());
        }
        LocalDateTime start = parseDay(request.getStartTime(), false);
        if (start != null) {
            qw.ge(GenerationLog::getCreateTime, start);
        }
        LocalDateTime endExclusive = parseDay(request.getEndTime(), true);
        if (endExclusive != null) {
            qw.lt(GenerationLog::getCreateTime, endExclusive);
        }
        // 刻意不接 PageRequest.sortField：那个字段会直接拼进 order by，是现成的注入面。
        // 日志按时间倒序是唯一有意义的顺序，写死即可。
        qw.orderByDesc(GenerationLog::getId);

        Page<GenerationLog> page = page(new Page<>(pageNum, pageSize), qw);
        List<GenerationLogVO> records = toVoList(page.getRecords());

        Page<GenerationLogVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(records);
        return result;
    }

    @Override
    public GenerationLogStatsVO stats(Integer days) {
        int window = days == null ? 7 : Math.min(90, Math.max(1, days));
        LocalDateTime start = LocalDate.now().minusDays(window - 1L).atStartOfDay();

        QueryWrapper<GenerationLog> totalQw = new QueryWrapper<>();
        totalQw.select(
                        "COUNT(*) AS total",
                        "SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END) AS success",
                        "SUM(CASE WHEN status = 2 THEN 1 ELSE 0 END) AS fail",
                        "COALESCE(SUM(totalTokens), 0) AS tokens",
                        "COALESCE(AVG(totalTokens), 0) AS avgTokens",
                        "COALESCE(AVG(durationMs), 0) AS avgDuration",
                        "COALESCE(AVG(rounds), 0) AS avgRounds")
                .ge("createTime", start);
        List<Map<String, Object>> totalRows = baseMapper.selectMaps(totalQw);
        Map<String, Object> total = totalRows.isEmpty() ? Collections.emptyMap() : totalRows.get(0);

        long totalCount = asLong(total.get("total"));
        long successCount = asLong(total.get("success"));
        long failCount = asLong(total.get("fail"));

        QueryWrapper<GenerationLog> dailyQw = new QueryWrapper<>();
        dailyQw.select(
                        "DATE(createTime) AS d",
                        "COUNT(*) AS n",
                        "SUM(CASE WHEN status = 2 THEN 1 ELSE 0 END) AS fail",
                        "COALESCE(SUM(totalTokens), 0) AS tokens")
                .ge("createTime", start)
                .groupBy("DATE(createTime)")
                .orderByAsc("d");

        Map<String, GenerationLogStatsVO.Daily> byDate = new HashMap<>();
        for (Map<String, Object> row : baseMapper.selectMaps(dailyQw)) {
            String key = String.valueOf(row.get("d"));
            byDate.put(key, GenerationLogStatsVO.Daily.builder()
                    .date(key)
                    .count(asLong(row.get("n")))
                    .failCount(asLong(row.get("fail")))
                    .tokens(asLong(row.get("tokens")))
                    .build());
        }

        // 没有数据的日期补 0：前端画折线图时不用自己补洞
        List<GenerationLogStatsVO.Daily> daily = new ArrayList<>(window);
        for (int i = 0; i < window; i++) {
            String key = LocalDate.now().minusDays(window - 1L - i).toString();
            daily.add(byDate.getOrDefault(key, GenerationLogStatsVO.Daily.builder()
                    .date(key).count(0L).failCount(0L).tokens(0L).build()));
        }

        return GenerationLogStatsVO.builder()
                .days(window)
                .totalCount(totalCount)
                .successCount(successCount)
                .failCount(failCount)
                .successRate(totalCount == 0 ? 0D : round1(successCount * 100D / totalCount))
                .totalTokens(asLong(total.get("tokens")))
                .avgTokens(asLong(total.get("avgTokens")))
                .avgDurationMs(asLong(total.get("avgDuration")))
                .avgRounds(round1(asDouble(total.get("avgRounds"))))
                .daily(daily)
                .build();
    }

    private List<GenerationLogVO> toVoList(List<GenerationLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return Collections.emptyList();
        }
        // 一次批量补齐账号：一页 10 条最多查一次，别在循环里查
        Set<Long> userIds = logs.stream()
                .map(GenerationLog::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = userIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        return logs.stream().map(logEntity -> {
            User user = logEntity.getUserId() == null ? null : userMap.get(logEntity.getUserId());
            return GenerationLogVO.builder()
                    .id(logEntity.getId())
                    .traceId(logEntity.getTraceId())
                    .userId(logEntity.getUserId())
                    .userAccount(user == null ? null : user.getUserAccount())
                    .userName(user == null ? null : user.getUserName())
                    .paperId(logEntity.getPaperId())
                    .paperName(logEntity.getPaperName())
                    .subject(logEntity.getSubject())
                    .gradeOrLevel(logEntity.getGradeOrLevel())
                    .model(logEntity.getModel())
                    .status(logEntity.getStatus())
                    .errorMsg(logEntity.getErrorMsg())
                    .durationMs(logEntity.getDurationMs())
                    .rounds(logEntity.getRounds())
                    .qualityScore(logEntity.getQualityScore())
                    .llmCalls(logEntity.getLlmCalls())
                    .promptTokens(logEntity.getPromptTokens())
                    .completionTokens(logEntity.getCompletionTokens())
                    .totalTokens(logEntity.getTotalTokens())
                    .byAgent(parseByAgent(logEntity.getByAgent()))
                    .usageSource(logEntity.getUsageSource())
                    .createTime(logEntity.getCreateTime())
                    .build();
        }).collect(Collectors.toList());
    }

    /** JSON 列 → Map，解析失败就当没有（前端少一行分摊明细，不影响列表展示） */
    private static Map<String, Integer> parseByAgent(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            JSONObject obj = JSONUtil.parseObj(json);
            Map<String, Integer> result = new LinkedHashMap<>();
            obj.forEach((key, value) -> result.put(key, value instanceof Number n ? n.intValue() : 0));
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析 yyyy-MM-dd。endExclusive = true 时返回次日 0 点，便于用 &lt; 做"含当天"的闭区间。
     * 解析不了就返回 null（忽略该条件），不让一个脏参数把整个请求打成 400。
     */
    private static LocalDateTime parseDay(String text, boolean endExclusive) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String s = text.trim();
        if (s.length() > 10) {
            s = s.substring(0, 10);
        }
        try {
            LocalDate day = LocalDate.parse(s);
            return endExclusive ? day.plusDays(1).atStartOfDay() : day.atStartOfDay();
        } catch (Exception e) {
            return null;
        }
    }

    private static long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    private static double asDouble(Object value) {
        return value instanceof Number n ? n.doubleValue() : 0D;
    }

    private static double round1(double v) {
        return Math.round(v * 10D) / 10D;
    }
}
