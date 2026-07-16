package com.ai.model.dto.knowledge;

import lombok.Data;

@Data
public class KnowledgeChatRequest {

    private Long conversationId;
    private Long knowledgeBaseId;
    private Long sourceDocumentId;
    private String mode = "knowledgeBase";
    private String question;
    private Integer topK;
}
