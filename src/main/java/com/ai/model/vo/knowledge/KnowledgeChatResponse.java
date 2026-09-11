package com.ai.model.vo.knowledge;

import lombok.Data;

import java.util.List;

@Data
public class KnowledgeChatResponse {

    private Long conversationId;
    private Long userMessageId;
    private Long assistantMessageId;
    private String answer;
    private List<KnowledgeReferenceVO> references;
}
