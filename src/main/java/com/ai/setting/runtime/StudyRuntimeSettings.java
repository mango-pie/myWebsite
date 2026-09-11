package com.ai.setting.runtime;

import com.ai.config.ConditionalOnModule;

import com.ai.constant.SiteSettingConstant;
import com.ai.service.SiteSettingService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * study 模块运行时读取：DB 覆盖默认值。
 */
@ConditionalOnModule("study")
@Component
public class StudyRuntimeSettings {

    @Resource
    private SiteSettingService siteSettingService;

    public int focusDefaultMinutes() {
        return siteSettingService.getInt(SiteSettingConstant.MODULE_STUDY, "focus.default_minutes", 25);
    }

    public int focusBreakMinutes() {
        return siteSettingService.getInt(SiteSettingConstant.MODULE_STUDY, "focus.break_minutes", 5);
    }

    public boolean habitReminderEnabledDefault() {
        return siteSettingService.getBool(SiteSettingConstant.MODULE_STUDY, "habit.reminder_enabled_default", true);
    }

    public int statsDefaultRangeDays() {
        return siteSettingService.getInt(SiteSettingConstant.MODULE_STUDY, "stats.default_range_days", 7);
    }

    public boolean workspaceShowChecklist() {
        return siteSettingService.getBool(SiteSettingConstant.MODULE_STUDY, "workspace.show_checklist", true);
    }
}
