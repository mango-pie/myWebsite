package com.ai.setting.module;

import com.ai.config.ConditionalOnModule;

import com.ai.constant.SiteSettingConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.setting.SettingFieldSchema;
import com.ai.setting.SettingModule;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@ConditionalOnModule("blog")
@Component
public class BlogModule implements SettingModule {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_OFFLINE = "OFFLINE";

    private static final Set<String> STATUSES = Set.of(STATUS_DRAFT, STATUS_PUBLISHED, STATUS_OFFLINE);

    @Override
    public String code() {
        return SiteSettingConstant.MODULE_BLOG;
    }

    @Override
    public String displayName() {
        return "博客";
    }

    @Override
    public String phase() {
        return "P4";
    }

    @Override
    public List<SettingFieldSchema> schema() {
        return List.of(
                intField("list.page_size_default", "默认列表页大小", 10, 1, 100),
                intField("post.summary_max_length", "摘要最大长度", 200, 20, 2000),
                bool("post.allow_like", "允许点赞", true, true),
                bool("post.view_count_enabled", "统计阅读量", true, false),
                enumStr("editor.default_status", "新建默认状态", STATUS_DRAFT, List.copyOf(STATUSES))
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
        if (items.containsKey("editor.default_status")) {
            String status = String.valueOf(items.get("editor.default_status")).trim().toUpperCase(Locale.ROOT);
            if (!STATUSES.contains(status)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR,
                        "editor.default_status 仅支持: DRAFT, PUBLISHED, OFFLINE");
            }
        }
    }

    /** DRAFT→0, PUBLISHED→1, OFFLINE→2 */
    public static int statusToInt(String status) {
        if (status == null) {
            return 0;
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case STATUS_PUBLISHED -> 1;
            case STATUS_OFFLINE -> 2;
            default -> 0;
        };
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

    private SettingFieldSchema enumStr(String key, String label, String def, List<String> enums) {
        return SettingFieldSchema.builder()
                .key(key).valueType(SiteSettingConstant.TYPE_STRING).label(label).description(label)
                .defaultValue(def).enumValues(enums).build();
    }
}
