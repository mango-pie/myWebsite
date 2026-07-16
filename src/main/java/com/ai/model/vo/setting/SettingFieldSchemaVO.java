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
public class SettingFieldSchemaVO {
    private String key;
    private String valueType;
    private String label;
    private String description;
    private Object defaultValue;
    private boolean sensitive;
    private Double min;
    private Double max;
    private List<String> enumValues;
    private boolean danger;
}
