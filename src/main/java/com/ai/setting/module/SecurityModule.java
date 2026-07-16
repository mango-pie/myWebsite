package com.ai.setting.module;

import com.ai.constant.SiteSettingConstant;
import com.ai.constant.UserConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.setting.SettingFieldSchema;
import com.ai.setting.SettingModule;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class SecurityModule implements SettingModule {

    private static final Set<String> ALLOWED_ROLES = Set.of(UserConstant.DEFAULT_ROLE);

    @Override
    public String code() {
        return SiteSettingConstant.MODULE_SECURITY;
    }

    @Override
    public String displayName() {
        return "安全";
    }

    @Override
    public String phase() {
        return "P0";
    }

    @Override
    public List<SettingFieldSchema> schema() {
        return List.of(
                SettingFieldSchema.builder()
                        .key("register_enabled")
                        .valueType(SiteSettingConstant.TYPE_BOOL)
                        .label("开放注册")
                        .description("关闭后新用户无法注册")
                        .defaultValue(true)
                        .danger(true)
                        .build(),
                SettingFieldSchema.builder()
                        .key("default_user_role")
                        .valueType(SiteSettingConstant.TYPE_STRING)
                        .label("默认用户角色")
                        .description("新注册用户的默认角色")
                        .defaultValue(UserConstant.DEFAULT_ROLE)
                        .enumValues(List.of(UserConstant.DEFAULT_ROLE))
                        .build(),
                SettingFieldSchema.builder()
                        .key("login_fail_hint_generic")
                        .valueType(SiteSettingConstant.TYPE_BOOL)
                        .label("登录失败模糊提示")
                        .description("开启后账号不存在与密码错误统一提示")
                        .defaultValue(true)
                        .build()
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
        if (items.containsKey("default_user_role")) {
            String role = String.valueOf(items.get("default_user_role"));
            if (!ALLOWED_ROLES.contains(role)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "默认角色不在白名单内: " + role);
            }
        }
    }
}
