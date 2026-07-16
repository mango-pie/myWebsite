package com.ai.model.vo.setting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettingModuleVO {
    private String code;
    private String displayName;
    private String phase;
    private boolean writable;
}
