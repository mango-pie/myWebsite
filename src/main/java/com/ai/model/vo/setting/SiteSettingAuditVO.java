package com.ai.model.vo.setting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteSettingAuditVO {
    private Long id;
    private String module;
    private String settingKey;
    private String oldValue;
    private String newValue;
    private Long operatorId;
    private String action;
    private LocalDateTime createTime;
}
