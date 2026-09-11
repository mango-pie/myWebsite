package com.ai.model.dto.knowledge;

import com.ai.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class KnowledgeBaseQueryRequest extends PageRequest {

    private String name;
    private String visibility;
    private Integer status;
}
