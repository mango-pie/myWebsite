package com.ai.model.dto.knowledge;

import lombok.Data;

@Data
public class KnowledgeTagRenameRequest {

    private String oldName;
    private String newName;
}
