package com.ai.setting.module;

import com.ai.constant.SiteSettingConstant;
import com.ai.setting.SettingFieldSchema;
import com.ai.setting.SettingModule;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class StudyModule implements SettingModule {

    @Override
    public String code() {
        return SiteSettingConstant.MODULE_STUDY;
    }

    @Override
    public String displayName() {
        return "学习系统";
    }

    @Override
    public String phase() {
        return "P4";
    }

    @Override
    public List<SettingFieldSchema> schema() {
        return List.of(
                intField("focus.default_minutes", "默认专注时长(分钟)", 25, 1, 180),
                intField("focus.break_minutes", "默认休息时长(分钟)", 5, 1, 60),
                bool("habit.reminder_enabled_default", "新习惯默认提醒", true, false),
                intField("stats.default_range_days", "统计默认天数", 7, 1, 366),
                bool("workspace.show_checklist", "工作台默认展示清单", true, false)
        );
    }

    @Override
    public Map<String, Object> defaults() {
        Map<String, Object> map = new LinkedHashMap<>();
        for (SettingFieldSchema field : schema()) {
            map.put(field.getKey(), field.getDefaultValue());
        }
        return map;
    }

    @Override
    public void validate(Map<String, Object> items) {
        // min/max covered by schema
    }

    private SettingFieldSchema bool(String key, String label, boolean def, boolean danger) {
        return SettingFieldSchema.builder()
                .key(key).valueType(SiteSettingConstant.TYPE_BOOL).label(label).description(label)
                .defaultValue(def).danger(danger).build();
    }

    private SettingFieldSchema intField(String key, String label, int def, double min, double max) {
        return SettingFieldSchema.builder()
                .key(key).valueType(SiteSettingConstant.TYPE_INT).label(label).description(label)
                .defaultValue(def).min(min).max(max).build();
    }
}
