package com.ai.model.dto.knowledge;

import com.ai.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class KnowledgeReadingJobQueryRequest extends PageRequest {

    /** 按状态过滤：PENDING/RUNNING/SUCCESS/FAILED */
    private String status;

    /** 只看进行中（PENDING/RUNNING），前端列表页轮询用 */
    private Boolean activeOnly;
}
