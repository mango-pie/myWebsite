package com.ai.setting.runtime;

import com.ai.constant.SiteSettingConstant;
import com.ai.service.SiteSettingService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * ops 模块运行时读取（平台横切，随 ops 设置项读取；Bean 常驻）。
 */
@Component
public class OpsRuntimeSettings {

    @Resource
    private SiteSettingService siteSettingService;

    public boolean debugExposeErrorDetail() {
        return siteSettingService.getBool(SiteSettingConstant.MODULE_OPS, "debug_expose_error_detail", false);
    }

    public boolean usageLogEnabled() {
        return siteSettingService.getBool(SiteSettingConstant.MODULE_OPS, "usage_log_enabled", false);
    }

    public int usageLogRetainDays() {
        return siteSettingService.getInt(SiteSettingConstant.MODULE_OPS, "usage_log_retain_days", 30);
    }

    public boolean opsAuditEnabled() {
        return siteSettingService.getBool(SiteSettingConstant.MODULE_OPS, "ops_audit_enabled", true);
    }

    public int opsAuditRetainDays() {
        return siteSettingService.getInt(SiteSettingConstant.MODULE_OPS, "ops_audit_retain_days", 90);
    }

    public boolean bizStatsEnabled() {
        return siteSettingService.getBool(SiteSettingConstant.MODULE_OPS, "biz_stats_enabled", true);
    }

    public boolean httpLogEnabled() {
        return siteSettingService.getBool(SiteSettingConstant.MODULE_OPS, "http_log_enabled", false);
    }

    public String httpLogMode() {
        return siteSettingService.getString(SiteSettingConstant.MODULE_OPS, "http_log_mode", "errors_only");
    }

    public int httpLogSlowMs() {
        return siteSettingService.getInt(SiteSettingConstant.MODULE_OPS, "http_log_slow_ms", 1000);
    }

    public int httpLogRetainDays() {
        return siteSettingService.getInt(SiteSettingConstant.MODULE_OPS, "http_log_retain_days", 14);
    }
}
