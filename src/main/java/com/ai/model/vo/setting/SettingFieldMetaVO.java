package com.ai.model.vo.setting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettingFieldMetaVO {
    /** db | default */
    private String source;
    private boolean sensitive;
    private boolean configured;
}
