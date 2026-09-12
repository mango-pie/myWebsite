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
    /** 归档过滤：null=只看未归档（默认隐藏归档），true=只看已归档，false=全部 */
    private Boolean archived;
    /** 复习状态过滤：NEW/REVIEWING/MASTERED */
    private String reviewStatus;
}
