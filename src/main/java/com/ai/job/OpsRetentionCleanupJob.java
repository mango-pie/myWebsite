package com.ai.job;

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
 */
@Slf4j
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

    @Scheduled(cron = "0 30 3 * * ?")
    public void purgeExpiredLogs() {
        try {
            int usageDeleted = aiUsageLogService.purgeExpired();
            int auditDeleted = opsAuditLogService.purgeExpired();
            int bizDeleted = bizStatDailyService.purgeExpired();
            int httpDeleted = httpAccessLogService.purgeExpired();
            if (usageDeleted > 0 || auditDeleted > 0 || bizDeleted > 0 || httpDeleted > 0) {
                log.info("Ops retention cleanup: ai_usage_log={}, ops_audit_log={}, biz_stat_daily={}, http_access_log={}",
                        usageDeleted, auditDeleted, bizDeleted, httpDeleted);
            }
        } catch (Exception e) {
            log.warn("Ops retention cleanup failed: {}", e.getMessage());
        }
    }
}
