package com.ai.model.dto.knowledge;

import com.ai.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class KnowledgeNoteQueryRequest extends PageRequest {

    private String keyword;
    private String sourceType;
    private String publishStatus;
    private String indexStatus;
}
