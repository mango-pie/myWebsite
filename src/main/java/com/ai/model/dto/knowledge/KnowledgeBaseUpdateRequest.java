package com.ai.model.dto.knowledge;

import lombok.Data;

@Data
public class KnowledgeBaseUpdateRequest {

    private String name;
    private String description;
    private String visibility = "PRIVATE";
    private Integer status = 1;
}
