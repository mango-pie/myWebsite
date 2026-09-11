package com.ai.setting.module;

import com.ai.constant.SiteSettingConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.setting.SettingFieldSchema;
import com.ai.setting.SettingModule;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SiteModule implements SettingModule {

    @Override
    public String code() {
        return SiteSettingConstant.MODULE_SITE;
    }

    @Override
    public String displayName() {
        return "站点";
    }

    @Override
    public String phase() {
        return "P0";
    }

    @Override
    public List<SettingFieldSchema> schema() {
        return List.of(
                SettingFieldSchema.builder()
                        .key("name")
                        .valueType(SiteSettingConstant.TYPE_STRING)
                        .label("站点名称")
                        .description("后台与前端展示用的站点名称")
                        .defaultValue("Ai Scene")
                        .build(),
                SettingFieldSchema.builder()
                        .key("slogan")
                        .valueType(SiteSettingConstant.TYPE_STRING)
                        .label("站点标语")
                        .description("一句话介绍，可空")
                        .defaultValue("")
                        .build(),
                SettingFieldSchema.builder()
                        .key("maintenance_mode")
                        .valueType(SiteSettingConstant.TYPE_BOOL)
                        .label("维护模式")
                        .description("开启后非管理员禁止写操作")
                        .defaultValue(false)
                        .danger(true)
                        .build(),
                SettingFieldSchema.builder()
                        .key("frontend_public_notice")
                        .valueType(SiteSettingConstant.TYPE_STRING)
                        .label("前端公告")
                        .description("前端展示的公告文案，可空")
                        .defaultValue("")
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
        if (items.containsKey("name")) {
            Object name = items.get("name");
            if (name == null || String.valueOf(name).isBlank()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "站点名称不能为空");
            }
            if (String.valueOf(name).length() > 64) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "站点名称过长");
            }
        }
        if (items.containsKey("slogan") && items.get("slogan") != null
                && String.valueOf(items.get("slogan")).length() > 256) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "站点标语过长");
        }
        if (items.containsKey("frontend_public_notice") && items.get("frontend_public_notice") != null
                && String.valueOf(items.get("frontend_public_notice")).length() > 2000) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "公告文案过长");
        }
    }
}
