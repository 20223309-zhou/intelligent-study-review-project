package com.zhou.review.controller;

import com.zhou.review.model.dto.exam.ExamPaperDto;
import com.zhou.review.model.entity.ExamPaper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.Map;

import static com.zhou.review.model.enums.QuestionConfigEnum.*;
import static com.zhou.review.model.enums.QuestionConfigEnum.ESSAY;

@SpringBootTest
public class ExamPaperControllerTest {
    @Resource
    private ExamPaperController examPaperController;
    @Test
    public void testAddExamPaper() {
        ExamPaperDto examPaperDto = ExamPaperDto.builder()
                .subject("马克思主义基本原理")
                .gradeOrLevel("大学")
                .userId(10086L)
                .questionConfig(Map.of(
                        SINGLE_CHOICE.getText(), 10,
                        MULTIPLE_CHOICE.getText(), 2,
                        SHORT_ANSWER.getText(), 5,
                        ESSAY.getText(), 2
                ))
                .textbookVersion("高等教育出版社2023版")
                .durationMinutes(90)
                .totalScore(new BigDecimal(50))
                .build();

    }
}
