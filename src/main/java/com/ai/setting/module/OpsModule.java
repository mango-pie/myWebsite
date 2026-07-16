package com.ai.setting.module;

import com.ai.constant.HttpLogModeConstant;
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

@Component
public class OpsModule implements SettingModule {

    @Override
    public String code() {
        return SiteSettingConstant.MODULE_OPS;
    }

    @Override
    public String displayName() {
        return "运维开关";
    }

    @Override
    public String phase() {
        return "P5";
    }

    @Override
    public List<SettingFieldSchema> schema() {
        return List.of(
                bool("debug_expose_error_detail", "对管理员暴露细错误", false, true),
                bool("usage_log_enabled", "启用 AI 调用用量日志", false, false),
                intField("usage_log_retain_days", "用量日志保留天数", 30, 1, 3650),
                bool("ops_audit_enabled", "启用通用操作审计", true, false),
                intField("ops_audit_retain_days", "操作审计保留天数", 90, 1, 3650),
                bool("biz_stats_enabled", "启用业务日统计", true, false),
                bool("http_log_enabled", "启用 HTTP 访问日志", false, false),
                enumStr("http_log_mode", "HTTP 日志模式", HttpLogModeConstant.ERRORS_ONLY,
                        List.of(HttpLogModeConstant.ERRORS_ONLY, HttpLogModeConstant.SLOW_AND_ERRORS, HttpLogModeConstant.ALL),
                        true),
                intField("http_log_slow_ms", "慢请求阈值(ms)", 1000, 1, 600_000),
                intField("http_log_retain_days", "HTTP 日志保留天数", 14, 1, 3650)
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
        if (items.containsKey("http_log_mode")) {
            String mode = String.valueOf(items.get("http_log_mode")).trim().toLowerCase(Locale.ROOT);
            if (!HttpLogModeConstant.isKnown(mode)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR,
                        "http_log_mode 仅支持: errors_only, slow_and_errors, all");
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

    private SettingFieldSchema enumStr(String key, String label, String def, List<String> enums, boolean danger) {
        return SettingFieldSchema.builder()
                .key(key).valueType(SiteSettingConstant.TYPE_STRING).label(label).description(label)
                .defaultValue(def).enumValues(enums).danger(danger)
                .sideEffect(danger ? "http_log_mode=all 会产生大量日志，谨慎开启" : null)
                .build();
    }
}
