package com.ai.model.vo.knowledge;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeNoteVO {

    private Long id;
    private Long sourceDocumentId;
    private Long blogPostId;
    private Long knowledgeDocumentId;
    private String title;
    private String tags;
    private String sourceType;
    private String sourceUrl;
    private String status;
    private String publishStatus;
    private String indexStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime lastEditedAt;
    private LocalDateTime lastPublishedAt;
    private LocalDateTime lastIndexedAt;
}
