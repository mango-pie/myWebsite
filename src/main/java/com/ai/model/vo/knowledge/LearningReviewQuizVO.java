package com.ai.model.vo.knowledge;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 复习模式：按枝抽取的自测题（答案折叠，供前端复习）。
 */
@Data
public class LearningReviewQuizVO {

    private Long branchId;
    private String branchTitle;
    private List<Question> questions = new ArrayList<>();

    @Data
    public static class Question {
        private String question;
        private String answer;
    }
}
