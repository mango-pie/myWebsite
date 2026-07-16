package com.ai.setting;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 单个设置字段的 schema 定义。
 */
@Data
@Builder
public class SettingFieldSchema {

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
    /** 危险项副作用说明（如需手动重建索引），供前端确认文案。 */
    private String sideEffect;
}
