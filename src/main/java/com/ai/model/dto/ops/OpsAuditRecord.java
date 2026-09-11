package com.ai.model.dto.ops;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpsAuditRecord {
    private Long operatorId;
    private String action;
    private String resourceType;
    private String resourceId;
    private String ip;
    private Map<String, Object> detail;
    private boolean success;
}
