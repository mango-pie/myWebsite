package com.ai.setting.runtime;

import com.ai.constant.SiteSettingConstant;
import com.ai.service.SiteSettingService;
import com.ai.setting.module.ReadingModule;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * reading 模块运行时读取：DB 覆盖默认值。
 */
@Component
public class ReadingRuntimeSettings {

    @Resource
    private SiteSettingService siteSettingService;

    public double distillTemperature() {
        return siteSettingService.getDouble(SiteSettingConstant.MODULE_READING, "distill.temperature", 0.2);
    }

    public int distillMaxTokens() {
        return siteSettingService.getInt(SiteSettingConstant.MODULE_READING, "distill.max_tokens", 4096);
    }

    public String distillSystemPrompt() {
        return siteSettingService.getString(SiteSettingConstant.MODULE_READING, "distill.system_prompt",
                ReadingModule.DEFAULT_DISTILL_SYSTEM_PROMPT);
    }

    public boolean publishDefaultAsDraft() {
        return siteSettingService.getBool(SiteSettingConstant.MODULE_READING, "publish.default_as_draft", true);
    }

    public boolean publishAskOpenEditor() {
        return siteSettingService.getBool(SiteSettingConstant.MODULE_READING, "publish.ask_open_editor", true);
    }

    public boolean redistillConfirmRequired() {
        return siteSettingService.getBool(SiteSettingConstant.MODULE_READING, "redistill.confirm_required", true);
    }

    public String searchProvider() {
        return siteSettingService.getString(SiteSettingConstant.MODULE_READING, "search.provider", "deepseek");
    }

    public String ingestSyncMode() {
        return siteSettingService.getString(SiteSettingConstant.MODULE_READING, "ingest.sync_mode", "sync");
    }

    public boolean ingestAsync() {
        return "async".equalsIgnoreCase(ingestSyncMode());
    }
}
