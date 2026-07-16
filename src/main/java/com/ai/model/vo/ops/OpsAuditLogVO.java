package com.ai.model.vo.ops;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpsAuditLogVO {
    private Long id;
    private Long operatorId;
    private String action;
    private String resourceType;
    private String resourceId;
    private String ip;
    private String detailJson;
    private Boolean success;
    private LocalDateTime createTime;
}
