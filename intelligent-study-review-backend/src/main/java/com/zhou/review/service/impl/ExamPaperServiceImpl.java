package com.zhou.review.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhou.review.agents.AsyncGenExamPaper;
import com.zhou.review.agents.ExamAiGrader;
import com.zhou.review.agents.progress.GenerationProgressSink;
import com.zhou.review.exception.BusinessException;
import com.zhou.review.exception.ErrorCode;
import com.zhou.review.model.dto.exam.ExamPaperQueryRequest;
import com.zhou.review.model.entity.ExamPaper;
import com.zhou.review.mapper.ExamPaperMapper;
import com.zhou.review.model.entity.Question;
import com.zhou.review.model.vo.ExamPaperGradeVO;
import com.zhou.review.model.vo.ExamPaperVO;
import com.zhou.review.model.vo.QuestionVO;
import com.zhou.review.service.ExamPaperService;
import com.zhou.review.service.QuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhou.review.utils.RedisCacheUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ExamPaperServiceImpl extends ServiceImpl<ExamPaperMapper, ExamPaper> implements ExamPaperService {

    /**
     * 走本地比对的题型。其余（简答/填空/证明/论述，以及材料题的简答子题）交由 AI 判分。
     */
    private static final Set<String> OBJECTIVE_TYPES = Set.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE");

    /**
     * 单题作答送判的截断长度
     */
    private static final int MAX_ANSWER_CHARS = 3000;

    @Resource
    private AsyncGenExamPaper asyncGenExamPaper;

    @Resource
    private ExamAiGrader examAiGrader;

    @Resource
    private QuestionService questionService;

    @Resource
    private RedisCacheUtil redisCacheUtil;

    @Resource
    private PlatformTransactionManager transactionManager;

    /**
     * 生成考试试卷
     * @param examPaper 试卷信息
     * @return 试卷信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamPaperVO generateExamPaper(ExamPaper examPaper) {
        return persistGenerated(examPaper, asyncGenExamPaper.generateExamPaper(examPaper));
    }

    /**
     * 带进度的生成试卷（SSE 流式端点使用）。
     * 刻意不在方法上加事务：生成要跑几十秒到几分钟，若把大模型调用包进事务，
     * 会长时间占着一条数据库连接，并发几个就把连接池吃满。只在落库那一步开事务。
     */
    @Override
    public ExamPaperVO generateExamPaperWithProgress(Long paperId, GenerationProgressSink sink) {
        ExamPaper examPaper = getById(paperId);
        if (examPaper == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "试卷不存在");
        }
        ExamPaperVO examPaperVO = asyncGenExamPaper.generateExamPaper(examPaper, sink);
        ExamPaperVO saved = new TransactionTemplate(transactionManager)
                .execute(status -> persistGenerated(examPaper, examPaperVO));
        // 落库成功后才通知"完成"——此时试卷ID与题目都已经入库
        if (sink != null) {
            sink.done(saved);
        }
        return saved;
    }

    /**
     * 生成结果的落库逻辑：同步 / 流式两条路共用
     */
    private ExamPaperVO persistGenerated(ExamPaper examPaper, ExamPaperVO examPaperVO) {
        if (examPaperVO == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "生成考试试卷失败");
        }
        BeanUtil.copyProperties(examPaperVO, examPaper,"id");
        examPaper.setGenerationStatus(1);
        // 更新试卷的生成状态
        boolean isSuccess = updateById(examPaper);
        if (!isSuccess) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "更新考试试卷失败");
        }

        // 开始解析生成的试卷VO
        List<QuestionVO> questions = examPaperVO.getQuestions();
        if (questions == null || questions.isEmpty()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "生成的试卷没有题目");
        }
        // 保存题目到 question 表，直接关联试卷ID
        questions.forEach(questionVO -> {
            Question question = BeanUtil.copyProperties(questionVO, Question.class);
            question.setPaperId(examPaper.getId());
            question.setSortOrder(questionVO.getSortOrder());
            question.setScore(questionVO.getScore());
            boolean save = questionService.save(question);
            if (!save) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "保存题目失败");
            }
        });
        return examPaperVO;
    }

    /**
     * 获取试卷列表
     * @param request 查询参数
     * @param userId 用户ID
     * @return 试卷列表
     */
    @Override
    public Page<ExamPaperVO> listPaperPages(ExamPaperQueryRequest request, Long userId) {
        // 构建缓存key，查找缓存
        String key = String.format("examPaper:list:%d", userId);
        Page<ExamPaperVO> cachedPage = redisCacheUtil.getObject(key, new TypeReference<Page<ExamPaperVO>>() {});
        if (cachedPage != null) {
            return cachedPage;
        }
        // 缓存不存在，重新查询
        Page<ExamPaper> examPaperPage = page(
                new Page<>(request.getPageNum(), request.getPageSize()),
                new LambdaQueryWrapper<ExamPaper>()
                        .eq(ExamPaper::getUserId, userId)
                        .orderByDesc(ExamPaper::getCreateTime));
        List<ExamPaperVO> voList = examPaperPage.getRecords().stream()
                .map(examPaper ->
                BeanUtil.copyProperties(examPaper, ExamPaperVO.class))
                .toList();
        voList.forEach(vo -> {
            ExamPaperVO paperDetail = getPaperDetail(vo.getId());
            if (paperDetail != null) {
                vo.setQuestions(paperDetail.getQuestions());
            }
        });

        Page<ExamPaperVO> resultPage = new Page<>(examPaperPage.getCurrent(), examPaperPage.getSize(), examPaperPage.getTotal());
        resultPage.setRecords(voList);
        redisCacheUtil.setObject(key, resultPage, 60, TimeUnit.MINUTES);
        return resultPage;
    }

    /**
     * 获取试卷详情
     * @param id 试卷ID
     * @return 试卷详情
     */
    @Override
    public ExamPaperVO getPaperDetail(Long id) {
        ExamPaper examPaper = getById(id);
        if (examPaper == null || examPaper.getIsDelete() == 1) return null;

        ExamPaperVO vo = BeanUtil.copyProperties(examPaper, ExamPaperVO.class);

        List<Question> questions = questionService.list(
                new LambdaQueryWrapper<Question>()
                        .eq(Question::getPaperId, id)
                        .orderByAsc(Question::getSortOrder));

        List<QuestionVO> questionVOs = questions.stream().map(q -> {
            QuestionVO qvo = BeanUtil.copyProperties(q, QuestionVO.class);
            qvo.setScore(q.getScore());
            qvo.setSortOrder(q.getSortOrder());
            return qvo;
        }).toList();
        vo.setQuestions(questionVOs);
        return vo;
    }

    /**
     * 删除试卷
     * @param id 试卷ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePaper(Long id) {
        ExamPaper examPaper = getById(id);
        if (examPaper == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "试卷不存在");
        }
        Long userId = examPaper.getUserId();
        // 逻辑删除试卷（自动转 UPDATE is_delete=1）
        removeById(id);
        // 逻辑删除试卷下的所有题目
        boolean isSuccess = questionService.remove(new LambdaQueryWrapper<Question>().eq(Question::getPaperId, id));
        if (!isSuccess) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "删除试卷下的题目失败");
        }
        // 构建缓存key，删除缓存
        String key = String.format("examPaper:list:%d", userId);
        boolean result = redisCacheUtil.deleteObject(key);
        if (!result) {
            log.warn("删除试卷缓存失败，key: {}", key);
        }
    }


    /**
     * 批改试卷。
     * 分两条路走：
     *   客观题（有选项 / 单选·多选·判断）：本地比对，比较前先归一化
     *   模型输出的多选答案可能是 "A,C,E" 或 "CAE"，不归一会把对的判成错的；
     *   主观题：整卷一次交给 {@link ExamAiGrader}，拿到部分分与评语；
     *   未作答的不送 AI（省 token），判分失败标记为"待评阅"而不是 0 分。
     *
     * @param paperId     试卷ID
     * @param userAnswers 题号 → 作答
     */
    @Override
    public ExamPaperGradeVO gradePaper(Long paperId, Map<Integer, String> userAnswers) {
        // 查询试卷所有题目
        List<Question> questions = questionService.list(
                new LambdaQueryWrapper<Question>()
                        .eq(Question::getPaperId, paperId)
                        .eq(Question::getIsDelete, 0)
                        .orderByAsc(Question::getSortOrder));

        if (questions.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "试卷不存在或没有题目");
        }

        int totalScore = 0;
        List<ExamPaperGradeVO.QuestionGradeVO> details = new ArrayList<>(questions.size());
        List<ExamAiGrader.GradeItem> subjectiveItems = new ArrayList<>();

        for (Question q : questions) {
            int sortOrder = q.getSortOrder();
            String correctAnswer = q.getAnswer() != null ? q.getAnswer().trim() : "";
            String userAnswer = userAnswers.getOrDefault(sortOrder, "").trim();
            int score = q.getScore() != null ? q.getScore() : 0;
            totalScore += score;
            // 对单选、多选、判断题进行改卷
            if (isObjective(q)) {
                boolean correct = answersMatch(correctAnswer, userAnswer);
                details.add(ExamPaperGradeVO.QuestionGradeVO.builder()
                        .sortOrder(sortOrder)
                        .correct(correct)
                        .userAnswer(userAnswer)
                        .correctAnswer(correctAnswer)
                        .score(correct ? score : 0)
                        .fullScore(score)
                        .pending(false)
                        .build());
                continue;
            }

            // 主观题：先占位，等下面批量判分回填
            boolean blank = userAnswer.isEmpty();
            details.add(ExamPaperGradeVO.QuestionGradeVO.builder()
                    .sortOrder(sortOrder)
                    .correct(false)
                    .userAnswer(userAnswer)
                    .correctAnswer(correctAnswer)
                    .score(0)
                    .fullScore(score)
                    // 未作答是明确的 0 分，不是"待评阅"
                    .pending(!blank)
                    .build());
            if (!blank) {
                subjectiveItems.add(ExamAiGrader.GradeItem.builder()
                        .sortOrder(sortOrder)
                        .questionType(q.getQuestionType())
                        .content(q.getContent())
                        .materialText(q.getMaterialText())
                        .fullScore(score)
                        .referenceAnswer(correctAnswer)
                        .analysis(q.getAnalysis())
                        .studentAnswer(truncate(userAnswer))
                        .build());
            }
        }

        // 主观题整卷一次判分（未作答的已在上面排除）
        if (!subjectiveItems.isEmpty()) {
            Map<Integer, ExamAiGrader.GradeOutcome> outcomes = examAiGrader.grade(paperId, subjectiveItems);
            for (ExamPaperGradeVO.QuestionGradeVO vo : details) {
                ExamAiGrader.GradeOutcome outcome = outcomes.get(vo.getSortOrder());
                if (outcome == null) {
                    continue;
                }
                int full = vo.getFullScore() == null ? 0 : vo.getFullScore();
                int got = outcome.getScore() == null ? 0 : outcome.getScore();
                vo.setScore(got);
                vo.setAiComment(outcome.getComment());
                vo.setPending(outcome.isPending());
                // 主观题"完全正确"= 拿满；部分得分由前端按 score/fullScore 识别
                vo.setCorrect(!outcome.isPending() && full > 0 && got >= full);
            }
        }

        int userScore = details.stream()
                .mapToInt(d -> d.getScore() == null ? 0 : d.getScore())
                .sum();

        return ExamPaperGradeVO.builder()
                .totalScore(totalScore)
                .userScore(userScore)
                .details(details)
                .build();
    }

    /**
     * 走本地比对的题型：选择题（含材料题的选择子题）。
     * 判定用"题型白名单 或 存在选项"，后者是为了兜住 MATERIAL 的混合子题
     * —— 它的 options 在选择题子题上非空、简答子题上为 null。
     */
    private static boolean isObjective(Question q) {
        String type = q.getQuestionType();
        if (type != null && OBJECTIVE_TYPES.contains(type.trim().toUpperCase(Locale.ROOT))) {
            return true;
        }
        return q.getOptions() != null && !q.getOptions().isEmpty();
    }

    /**
     * 比对客观题作答。比较前统一归一化，避免因写法差异把正确答案判错。
     * 参考答案为空时一律判错（没有依据给分）。
     */
    private static boolean answersMatch(String correctAnswer, String userAnswer) {
        String expected = normalizeAnswer(correctAnswer);
        return !expected.isEmpty() && expected.equals(normalizeAnswer(userAnswer));
    }

    /**
     * 答案归一化：
     * <ul>
     *   <li>判断题的各种写法（对/错、T/F、√/×、true/false）统一成 T / F；</li>
     *   <li>选择题去掉分隔符后按字母排序，使 "A,C,E"、"CAE"、"c a e" 等价。</li>
     * </ul>
     */
    private static String normalizeAnswer(String raw) {
        if (raw == null) {
            return "";
        }
        String compact = raw.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s　]", "");
        if (compact.isEmpty()) {
            return "";
        }
        switch (compact) {
            case "对", "T", "TRUE", "√", "正确", "是", "Y", "YES" -> {
                return "T";
            }
            case "错", "F", "FALSE", "×", "X", "错误", "否", "N", "NO" -> {
                return "F";
            }
            default -> {
                // fallthrough：继续按选择题处理
            }
        }

        String letters = compact.replaceAll("[^A-Z]", "");
        String withoutSeparators = compact.replaceAll("[\\s,，、;；/|]", "");
        // 仅当选项目本身只由字母与分隔符组成时才做排序归一，避免误伤带字母的主观作答
        if (!letters.isEmpty() && letters.length() == withoutSeparators.length()) {
            char[] chars = letters.toCharArray();
            Arrays.sort(chars);
            return new String(chars);
        }
        return compact;
    }

    /**
     * 截断超长作答，防止单题把整批 prompt 撑爆。
     */
    private static String truncate(String text) {
        if (text == null || text.length() <= MAX_ANSWER_CHARS) {
            return text;
        }
        log.warn("作答超长已截断：原长 {} 字", text.length());
        return text.substring(0, MAX_ANSWER_CHARS);
    }
}
