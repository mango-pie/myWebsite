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
@Table("knowledge_note")
public class KnowledgeNote implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private Long sourceDocumentId;
    private Long blogPostId;
    private Long knowledgeDocumentId;
    private String title;
    private String distilledMd;
    private String tags;
    private String status;
    private String publishStatus;
    private String indexStatus;
    private String errorMsg;
    private Integer viewCount;
    private LocalDateTime lastDistilledAt;
    private LocalDateTime lastEditedAt;
    private LocalDateTime lastPublishedAt;
    private LocalDateTime lastIndexedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @Column(value = "is_delete", isLogicDelete = true)
    private Integer isDelete;
}
