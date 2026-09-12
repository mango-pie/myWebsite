package com.ai.model.dto.knowledge;

import lombok.Data;

@Data
public class KnowledgeSearchPreviewRequest {

    private String goal;
    private String preference;
    /** V3 树模式 */
    private Long domainId;
    private String gatePassId;
    private Long branchId;
    private Boolean skipGate;
}
