package com.zhou.review.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamPaperGradeVO {
    private Integer totalScore;
    private Integer userScore;
    private List<QuestionGradeVO> details;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionGradeVO {
        private Integer sortOrder;
        /**
         * 是否完全正确。主观题由「得分 == 满分」推导，因此部分得分不会标成 correct，
         * 前端需结合 score / fullScore 展示"部分得分"这一态。
         */
        private Boolean correct;
        private String userAnswer;
        private String correctAnswer;
        /**
         * 该题得分。主观题 AI 判分后是部分分（如 7/10），不再是满分或 0 的二值
         */
        private Integer score;
        /**
         * 该题满分。前端据此识别部分得分（0 < score < fullScore）
         */
        private Integer fullScore;
        /**
         * AI 评语（仅主观题有）：说明得分点与失分原因
         */
        private String aiComment;
        /**
         * 待评阅：AI 判分失败或未返回该题，此题的 score 不可信，前端应提示"待评阅"而非判错
         */
        private Boolean pending;
    }
}
