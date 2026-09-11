package com.ai.model.vo.setting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettingModuleSchemaVO {
    private String module;
    private String displayName;
    private List<SettingFieldSchemaVO> fields;
}
