package com.ai.setting.module;

import com.ai.config.ConditionalOnModule;

import com.ai.constant.SiteSettingConstant;
import com.ai.setting.SettingFieldSchema;
import com.ai.setting.SettingModule;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConditionalOnModule("diary")
@Component
public class DiaryModule implements SettingModule {

    @Override
    public String code() {
        return SiteSettingConstant.MODULE_DIARY;
    }

    @Override
    public String displayName() {
        return "日记";
    }

    @Override
    public String phase() {
        return "P4";
    }

    @Override
    public List<SettingFieldSchema> schema() {
        return List.of(
                bool("privacy.default_private", "新日记默认私密", true, false),
                bool("export.enabled", "开放导出", false, false),
                intField("list.page_size_default", "列表默认页大小", 20, 1, 100)
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
