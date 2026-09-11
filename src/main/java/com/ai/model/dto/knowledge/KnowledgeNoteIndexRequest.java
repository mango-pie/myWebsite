package com.ai.model.dto.knowledge;

import lombok.Data;

@Data
public class KnowledgeNoteIndexRequest {

    private Long knowledgeBaseId;
    private String knowledgeBaseName;
    private String knowledgeBaseDescription;
}
