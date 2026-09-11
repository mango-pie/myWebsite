package com.ai.model.vo.knowledge;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeConversationVO {

    private Long id;
    private Long userId;
    private Long knowledgeBaseId;
    private String title;
    private String lastMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
