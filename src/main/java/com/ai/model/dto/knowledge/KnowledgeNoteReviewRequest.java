package com.ai.model.dto.knowledge;

import lombok.Data;

@Data
public class KnowledgeNoteReviewRequest {

    /** NEW / REVIEWING / MASTERED */
    private String reviewStatus;
}
