package com.ai.model.dto.knowledge;

import com.ai.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class KnowledgeDocumentQueryRequest extends PageRequest {

    private String fileName;
    private String fileType;
    private String parseStatus;
}
