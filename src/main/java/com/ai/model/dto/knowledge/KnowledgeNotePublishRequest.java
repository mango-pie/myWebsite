package com.ai.model.dto.knowledge;

import lombok.Data;

import java.util.List;

@Data
public class KnowledgeNotePublishRequest {

    private Long categoryId;
    private List<Long> tagIds;
    /** 0 草稿，1 发布 */
    private Integer status;
}
