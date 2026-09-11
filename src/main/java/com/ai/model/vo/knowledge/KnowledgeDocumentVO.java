package com.ai.model.vo.knowledge;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeDocumentVO {

    private Long id;
    private Long knowledgeBaseId;
    private Long userId;
    private Long sourceDocumentId;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String bucketName;
    private String objectKey;
    private String parseStatus;
    private Integer chunkCount;
    private String errorMessage;
    private LocalDateTime parsedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
