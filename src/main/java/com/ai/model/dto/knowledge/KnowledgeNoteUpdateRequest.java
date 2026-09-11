package com.ai.model.dto.knowledge;

import lombok.Data;

@Data
public class KnowledgeNoteUpdateRequest {

    private String title;
    private String tags;
    private String distilledMd;
}
