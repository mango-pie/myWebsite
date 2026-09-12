package com.ai.model.vo.knowledge;

import lombok.Data;

import java.util.List;

@Data
public class LearningLeafBatchSuggestVO {

    private List<Suggestion> suggestions;

    @Data
    public static class Suggestion {
        private Long noteId;
        private Long branchId;
        private String newBranchTitle;
        private Long parentBranchId;
        private String reason;
    }
}
