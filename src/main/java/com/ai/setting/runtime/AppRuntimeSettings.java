package com.ai.setting.runtime;

import com.ai.constant.SiteSettingConstant;
import com.ai.model.enums.CodeGenTypeEnum;
import com.ai.service.SiteSettingService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * app 模块运行时读取：DB 覆盖默认值。
 */
@Component
public class AppRuntimeSettings {

    @Resource
    private SiteSettingService siteSettingService;

    public boolean codegenEnabled() {
        return siteSettingService.getBool(SiteSettingConstant.MODULE_APP, "codegen.enabled", true);
    }

    public String codegenDefaultType() {
        return siteSettingService.getString(SiteSettingConstant.MODULE_APP, "codegen.default_type",
                CodeGenTypeEnum.HTML.getValue());
    }

    public boolean deployEnabled() {
        return siteSettingService.getBool(SiteSettingConstant.MODULE_APP, "deploy.enabled", true);
    }

    public String deployPublicHostDisplay() {
        return siteSettingService.getString(SiteSettingConstant.MODULE_APP, "deploy.public_host_display", "");
    }
}
