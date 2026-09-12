package com.ai.model.dto.knowledge;

import lombok.Data;

@Data
public class LearningDomainCreateRequest {

    private String name;
    private Integer sortOrder;
}
