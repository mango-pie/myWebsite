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
import java.util.Map;
import java.util.Set;

@ConditionalOnModule("reading")
@Component
public class ReadingModule implements SettingModule {

    public static final String DEFAULT_DISTILL_SYSTEM_PROMPT = """
            请将下面内容蒸馏成适合反复复习的 Markdown 精读文章。
            输出结构必须包含：核心思想、关键知识点（3到5个）、代码/配置精髓、延伸问题。""";

    private static final Set<String> SEARCH_PROVIDERS = Set.of("deepseek", "tavily", "placeholder");
    private static final Set<String> SYNC_MODES = Set.of("sync", "async");

    @Override
    public String code() {
        return SiteSettingConstant.MODULE_READING;
    }

    @Override
    public String displayName() {
        return "精读工作台";
    }

    @Override
    public String phase() {
        return "P3";
    }

    @Override
    public List<SettingFieldSchema> schema() {
        return List.of(
                dbl("distill.temperature", "蒸馏温度", 0.2, 0, 2),
                intField("distill.max_tokens", "蒸馏 max tokens", 4096, 256, 32768),
                text("distill.system_prompt", "蒸馏系统 Prompt", DEFAULT_DISTILL_SYSTEM_PROMPT),

                bool("publish.default_as_draft", "发布默认草稿", true, false),
                bool("publish.ask_open_editor", "发布后提示打开编辑器", true, false),
                bool("redistill.confirm_required", "重新蒸馏需二次确认", true, false),

                enumStr("search.provider", "搜索提供方", "deepseek", List.copyOf(SEARCH_PROVIDERS)),
                enumStr("ingest.sync_mode", "采集模式", "async", List.copyOf(SYNC_MODES))
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
        if (items.containsKey("distill.system_prompt")) {
            Object prompt = items.get("distill.system_prompt");
            if (prompt == null || String.valueOf(prompt).isBlank()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "distill.system_prompt 不能为空");
            }
            if (String.valueOf(prompt).length() > 16000) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "distill.system_prompt 过长");
            }
        }
        if (items.containsKey("search.provider")) {
            String provider = String.valueOf(items.get("search.provider")).trim().toLowerCase();
            if (!SEARCH_PROVIDERS.contains(provider)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR,
                        "search.provider 仅支持: " + String.join(",", SEARCH_PROVIDERS));
            }
        }
        if (items.containsKey("ingest.sync_mode")) {
            String mode = String.valueOf(items.get("ingest.sync_mode")).trim().toLowerCase();
            if (!SYNC_MODES.contains(mode)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR,
                        "ingest.sync_mode 仅支持: sync, async");
            }
        }
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

    private SettingFieldSchema dbl(String key, String label, double def, double min, double max) {
        return SettingFieldSchema.builder()
                .key(key).valueType(SiteSettingConstant.TYPE_DOUBLE).label(label).description(label)
                .defaultValue(def).min(min).max(max).build();
    }

    private SettingFieldSchema text(String key, String label, String def) {
        return SettingFieldSchema.builder()
                .key(key).valueType(SiteSettingConstant.TYPE_STRING).label(label).description(label)
                .defaultValue(def == null ? "" : def).build();
    }

    private SettingFieldSchema enumStr(String key, String label, String def, List<String> enums) {
        return SettingFieldSchema.builder()
                .key(key).valueType(SiteSettingConstant.TYPE_STRING).label(label).description(label)
                .defaultValue(def).enumValues(enums).build();
    }
}
