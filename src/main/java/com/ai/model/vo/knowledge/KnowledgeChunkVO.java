package com.ai.model.vo.knowledge;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChunkVO {

    private Long id;
    private Long knowledgeBaseId;
    private Long knowledgeDocumentId;
    private Long sourceDocumentId;
    private Integer chunkIndex;
    private String heading;
    private String content;
    private Integer tokenCount;
    private String metadata;
    private LocalDateTime createTime;
    private Double score;
}
