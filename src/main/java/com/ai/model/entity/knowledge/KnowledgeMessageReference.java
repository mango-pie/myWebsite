package com.ai.model.entity.knowledge;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Table("knowledge_message_reference")
public class KnowledgeMessageReference implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private Long messageId;
    private Long knowledgeDocumentId;
    private Long sourceDocumentId;
    private Long chunkId;
    private Integer chunkIndex;
    private String documentName;
    private String content;
    private BigDecimal similarity;
    private LocalDateTime createTime;

    @Column(value = "is_delete", isLogicDelete = true)
    private Integer isDelete;
}
