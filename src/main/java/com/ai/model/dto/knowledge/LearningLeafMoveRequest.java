package com.ai.model.dto.knowledge;

import lombok.Data;

@Data
public class LearningLeafMoveRequest {

    private Long noteId;
    private Long targetBranchId;
}
