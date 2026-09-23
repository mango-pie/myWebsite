package com.ai.job;

import com.ai.config.ConditionalOnModule;

import com.ai.service.AiUsageLogService;
import com.ai.service.BizStatDailyService;
import com.ai.service.HttpAccessLogService;
import com.ai.service.OpsAuditLogService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 按保留策略清理用量、操作审计、业务日统计、HTTP 访问日志。
 * 四类日志各自独立清理，一类失败不影响其余。
 */
@Slf4j
@ConditionalOnModule("ops")
@Component
public class OpsRetentionCleanupJob {

    @Resource
    private AiUsageLogService aiUsageLogService;

    @Resource
    private OpsAuditLogService opsAuditLogService;

    @Resource
    private BizStatDailyService bizStatDailyService;

    @Resource
    private HttpAccessLogService httpAccessLogService;

    @Scheduled(cron = "${app.jobs.ops-retention-cron:0 30 3 * * ?}")
    public void purgeExpiredLogs() {
        int usageDeleted = purgeOne("ai_usage_log", aiUsageLogService::purgeExpired);
        int auditDeleted = purgeOne("ops_audit_log", opsAuditLogService::purgeExpired);
        int bizDeleted = purgeOne("biz_stat_daily", bizStatDailyService::purgeExpired);
        int httpDeleted = purgeOne("http_access_log", httpAccessLogService::purgeExpired);
        if (usageDeleted > 0 || auditDeleted > 0 || bizDeleted > 0 || httpDeleted > 0) {
            log.info("Ops retention cleanup: ai_usage_log={}, ops_audit_log={}, biz_stat_daily={}, http_access_log={}",
                    usageDeleted, auditDeleted, bizDeleted, httpDeleted);
        }
    }

    private int purgeOne(String name, java.util.function.IntSupplier purge) {
        try {
            return purge.getAsInt();
        } catch (Exception e) {
            log.warn("Ops retention cleanup failed for {}: {}", name, e.getMessage());
            return 0;
        }
    }
}
