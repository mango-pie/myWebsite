package com.ai.model.dto.knowledge;

import lombok.Data;

@Data
public class LearningDomainUpdateRequest {

    private String name;
    private Integer sortOrder;
}
