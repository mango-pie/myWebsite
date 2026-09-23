package com.ai.job;

import com.ai.config.ConditionalOnModule;
import com.ai.service.ops.OpsLogStreamService;
import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * SSE 日志流心跳，防止代理断开长连接。调度统一放在 job/ 包，业务留在 service。
 */
@ConditionalOnModule("ops")
@Component
public class OpsLogStreamHeartbeatJob {

    @Resource
    private OpsLogStreamService opsLogStreamService;

    @Scheduled(fixedDelayString = "${app.jobs.ops-log-heartbeat-ms:15000}")
    public void heartbeat() {
        opsLogStreamService.heartbeat();
    }
}
