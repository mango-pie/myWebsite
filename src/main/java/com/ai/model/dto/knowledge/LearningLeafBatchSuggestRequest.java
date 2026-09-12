package com.ai.model.dto.knowledge;

import lombok.Data;

import java.util.List;

@Data
public class LearningLeafBatchSuggestRequest {

    private Long domainId;
    private List<NoteBrief> notes;

    @Data
    public static class NoteBrief {
        private Long noteId;
        private String title;
        private String summary;
    }
}
