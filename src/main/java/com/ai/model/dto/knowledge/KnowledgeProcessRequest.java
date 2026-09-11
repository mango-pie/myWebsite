package com.ai.model.dto.knowledge;

import lombok.Data;

@Data
public class KnowledgeProcessRequest {

    private String inputType;
    private String url;
    private Long knowledgeBaseId;
    private String title;
    private String tags;
    private Boolean generateSummary = true;
    private Boolean enableRag = true;
}
