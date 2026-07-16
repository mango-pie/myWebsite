package com.ai.controller;

import com.ai.annotation.AuthCheck;
import com.ai.common.BaseResponse;
import com.ai.common.ResultUtils;
import com.ai.constant.UserConstant;
import com.ai.model.vo.ops.AiUsageLogVO;
import com.ai.model.vo.ops.AiUsageSummaryVO;
import com.ai.model.vo.ops.BizStatSeriesPointVO;
import com.ai.model.vo.ops.BizStatsOverviewVO;
import com.ai.model.vo.ops.HttpAccessLogVO;
import com.ai.model.vo.ops.OpsAuditLogVO;
import com.ai.service.AiUsageLogService;
import com.ai.service.BizStatDailyService;
import com.ai.service.HttpAccessLogService;
import com.ai.service.OpsAuditLogService;
import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 运维可观测 Admin API（Phase A～D）。
 */
@RestController
@RequestMapping("/admin/ops")
public class AdminOpsController {

    @Resource
    private AiUsageLogService aiUsageLogService;

    @Resource
    private OpsAuditLogService opsAuditLogService;

    @Resource
    private BizStatDailyService bizStatDailyService;

    @Resource
    private HttpAccessLogService httpAccessLogService;

    @GetMapping("/usage/summary")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<AiUsageSummaryVO> usageSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResultUtils.success(aiUsageLogService.summary(from, to));
    }

    @GetMapping("/usage/logs")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<AiUsageLogVO>> usageLogs(
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResultUtils.success(aiUsageLogService.pageLogs(scene, userId, from, to, pageNum, pageSize));
    }

    @GetMapping("/audit")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<OpsAuditLogVO>> audit(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResultUtils.success(opsAuditLogService.page(action, operatorId, from, to, pageNum, pageSize));
    }

    @GetMapping("/stats/overview")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<BizStatsOverviewVO> statsOverview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResultUtils.success(bizStatDailyService.overview(from, to));
    }

    @GetMapping("/stats/series")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<List<BizStatSeriesPointVO>> statsSeries(
            @RequestParam String metric,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResultUtils.success(bizStatDailyService.series(metric, from, to));
    }

    @GetMapping("/access-logs")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<HttpAccessLogVO>> accessLogs(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String statusClass,
            @RequestParam(required = false) String pathPrefix,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResultUtils.success(httpAccessLogService.page(
                status, statusClass, pathPrefix, from, to, pageNum, pageSize));
    }
}
