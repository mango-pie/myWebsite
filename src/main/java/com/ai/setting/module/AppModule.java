package com.ai.setting.module;

import com.ai.constant.SiteSettingConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.model.enums.CodeGenTypeEnum;
import com.ai.setting.SettingFieldSchema;
import com.ai.setting.SettingModule;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AppModule implements SettingModule {

    private static final Set<String> CODEGEN_TYPES = Arrays.stream(CodeGenTypeEnum.values())
            .map(CodeGenTypeEnum::getValue)
            .collect(Collectors.toUnmodifiableSet());

    @Override
    public String code() {
        return SiteSettingConstant.MODULE_APP;
    }

    @Override
    public String displayName() {
        return "应用生成";
    }

    @Override
    public String phase() {
        return "P4";
    }

    @Override
    public List<SettingFieldSchema> schema() {
        return List.of(
                bool("codegen.enabled", "开放应用生成", true, true),
                enumStr("codegen.default_type", "默认生成类型", CodeGenTypeEnum.HTML.getValue(),
                        List.copyOf(CODEGEN_TYPES)),
                bool("deploy.enabled", "开放一键部署", true, true),
                str("deploy.public_host_display", "部署访问前缀展示", "")
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
        if (items.containsKey("codegen.default_type")) {
            String type = String.valueOf(items.get("codegen.default_type")).trim().toLowerCase(Locale.ROOT);
            if (!CODEGEN_TYPES.contains(type)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR,
                        "codegen.default_type 仅支持: " + String.join(", ", CODEGEN_TYPES));
            }
        }
    }

    private SettingFieldSchema bool(String key, String label, boolean def, boolean danger) {
        return SettingFieldSchema.builder()
                .key(key).valueType(SiteSettingConstant.TYPE_BOOL).label(label).description(label)
                .defaultValue(def).danger(danger).build();
    }

    private SettingFieldSchema str(String key, String label, String def) {
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
