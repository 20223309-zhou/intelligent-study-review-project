package com.zhou.review.service.impl;

import com.zhou.review.model.entity.Question;
import com.zhou.review.mapper.QuestionMapper;
import com.zhou.review.service.QuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 题库表 服务实现类
 * </p>
 *
 * @author zhou
 * @since 2026-06-24
 */
@Service
public class QuestionServiceImpl extends ServiceImpl<QuestionMapper, Question> implements QuestionService {

}
