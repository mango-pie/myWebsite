package com.ai.model.entity.knowledge;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Table("knowledge_document")
public class KnowledgeDocument implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
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

    @Column(value = "is_delete", isLogicDelete = true)
    private Integer isDelete;
}
