package com.ai.setting.runtime;

import com.ai.constant.SiteSettingConstant;
import com.ai.service.SiteSettingService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * diary 模块运行时读取：DB 覆盖默认值。
 */
@Component
public class DiaryRuntimeSettings {

    @Resource
    private SiteSettingService siteSettingService;

    public boolean privacyDefaultPrivate() {
        return siteSettingService.getBool(SiteSettingConstant.MODULE_DIARY, "privacy.default_private", true);
    }

    public boolean exportEnabled() {
        return siteSettingService.getBool(SiteSettingConstant.MODULE_DIARY, "export.enabled", false);
    }

    public int listPageSizeDefault() {
        return siteSettingService.getInt(SiteSettingConstant.MODULE_DIARY, "list.page_size_default", 20);
    }

    public int resolvePageSize(int pageSize) {
        return pageSize <= 0 ? listPageSizeDefault() : pageSize;
    }

    /** 私密→草稿 0，非私密→完成 1 */
    public int defaultStatusWhenNull() {
        return privacyDefaultPrivate() ? 0 : 1;
    }
}
