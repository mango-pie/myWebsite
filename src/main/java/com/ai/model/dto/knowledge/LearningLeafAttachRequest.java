package com.ai.model.dto.knowledge;

import lombok.Data;

@Data
public class LearningLeafAttachRequest {

    private Long domainId;
    private Long noteId;
    private Long branchId;
    /** 确认屏顺带建枝 */
    private String newBranchTitle;
    private Long parentBranchId;
}
