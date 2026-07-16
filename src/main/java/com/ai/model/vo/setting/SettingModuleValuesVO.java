package com.ai.model.vo.setting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettingModuleValuesVO {
    private String module;
    private Map<String, Object> values;
    private Map<String, SettingFieldMetaVO> meta;
}
