package com.ai.model.dto.knowledge;

import com.ai.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class KnowledgeReadingJobQueryRequest extends PageRequest {

    public KnowledgeReadingJobQueryRequest() {
        setPageSize(20);
    }

    /** PENDING / RUNNING / SUCCESS / FAILED */
    private String status;

    /** true：仅进行中（PENDING + RUNNING） */
    private Boolean activeOnly;
}
