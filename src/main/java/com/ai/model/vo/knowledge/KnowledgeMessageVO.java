package com.ai.model.vo.knowledge;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class KnowledgeMessageVO {

    private Long id;
    private Long conversationId;
    private Long userId;
    private String role;
    private String content;
    private String modelName;
    private LocalDateTime createTime;
    private List<KnowledgeReferenceVO> references;
}
