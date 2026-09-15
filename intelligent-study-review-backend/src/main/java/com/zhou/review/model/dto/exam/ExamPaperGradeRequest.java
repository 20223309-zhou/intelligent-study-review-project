package com.zhou.review.model.dto.exam;

import lombok.Data;
import java.util.Map;

@Data
public class ExamPaperGradeRequest {
    private Long paperId;
    private Map<Integer, String> answers;
}
