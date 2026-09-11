package com.ai.model.dto.knowledge;

import lombok.Data;

@Data
public class KnowledgeBaseCreateRequest {

    private String name;
    private String description;
    private String visibility = "PRIVATE";
}
