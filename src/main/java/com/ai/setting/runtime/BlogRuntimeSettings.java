package com.ai.setting.runtime;

import com.ai.config.ConditionalOnModule;

import com.ai.constant.SiteSettingConstant;
import com.ai.service.SiteSettingService;
import com.ai.setting.module.BlogModule;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * blog 模块运行时读取：DB 覆盖默认值。
 */
@ConditionalOnModule("blog")
@Component
public class BlogRuntimeSettings {

    @Resource
    private SiteSettingService siteSettingService;

    public int listPageSizeDefault() {
        return siteSettingService.getInt(SiteSettingConstant.MODULE_BLOG, "list.page_size_default", 10);
    }

    public int summaryMaxLength() {
        return siteSettingService.getInt(SiteSettingConstant.MODULE_BLOG, "post.summary_max_length", 200);
    }

    public boolean allowLike() {
        return siteSettingService.getBool(SiteSettingConstant.MODULE_BLOG, "post.allow_like", true);
    }

    public boolean viewCountEnabled() {
        return siteSettingService.getBool(SiteSettingConstant.MODULE_BLOG, "post.view_count_enabled", true);
    }

    public String editorDefaultStatus() {
        return siteSettingService.getString(SiteSettingConstant.MODULE_BLOG, "editor.default_status",
                BlogModule.STATUS_DRAFT);
    }

    public int editorDefaultStatusInt() {
        return BlogModule.statusToInt(editorDefaultStatus());
    }

    public int resolvePageSize(int pageSize) {
        return pageSize <= 0 ? listPageSizeDefault() : pageSize;
    }
}
