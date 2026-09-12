package com.ai.model.dto.knowledge;

import lombok.Data;

@Data
public class LearningBranchUpdateRequest {

    private String title;
    private Long parentBranchId;
    private Integer sortOrder;
}
