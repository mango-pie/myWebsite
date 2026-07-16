package com.ai.model.vo.knowledge;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class KnowledgeReferenceVO {

    private Long chunkId;
    private Long knowledgeDocumentId;
    private Long sourceDocumentId;
    private Integer chunkIndex;
    private String documentName;
    private String content;
    private BigDecimal similarity;
}
