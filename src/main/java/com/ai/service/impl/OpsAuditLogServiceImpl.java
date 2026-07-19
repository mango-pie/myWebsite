package com.ai.service.impl;

import com.ai.config.ConditionalOnModule;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.ai.mapper.ops.OpsAuditLogMapper;
import com.ai.model.dto.ops.OpsAuditRecord;
import com.ai.model.entity.OpsAuditLog;
import com.ai.model.vo.ops.OpsAuditLogVO;
import com.ai.service.OpsAuditLogService;
import com.ai.setting.runtime.OpsRuntimeSettings;
import com.ai.utils.RequestClientUtils;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@ConditionalOnModule("ops")
@Service
public class OpsAuditLogServiceImpl implements OpsAuditLogService {

    private static final int MAX_DETAIL = 2048;
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "userpassword", "apikey", "api_key", "token", "secret", "authorization"
    );

    @Resource
    private OpsAuditLogMapper opsAuditLogMapper;

    @Resource
    private OpsRuntimeSettings opsRuntimeSettings;

    @Override
    public void record(OpsAuditRecord record) {
        if (record == null || !opsRuntimeSettings.opsAuditEnabled()) {
            return;
        }
        OpsAuditLog row = OpsAuditLog.builder()
                .operatorId(record.getOperatorId())
                .action(StrUtil.blankToDefault(record.getAction(), "unknown"))
                .resourceType(truncate(record.getResourceType(), 64))
                .resourceId(truncate(record.getResourceId(), 64))
                .ip(truncate(record.getIp(), 64))
                .detailJson(toSafeDetailJson(record.getDetail()))
                .success(record.isSuccess() ? 1 : 0)
                .createTime(LocalDateTime.now())
                .build();
        CompletableFuture.runAsync(() -> {
            try {
                opsAuditLogMapper.insert(row);
            } catch (Exception e) {
                log.warn("Write ops_audit_log failed: {}", e.getMessage());
            }
        });
    }

    @Override
    public void audit(String action, Long operatorId, String resourceType, String resourceId,
                      boolean success, Map<String, Object> detail, HttpServletRequest request) {
        record(OpsAuditRecord.builder()
                .action(action)
                .operatorId(operatorId)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .success(success)
                .detail(detail)
                .ip(RequestClientUtils.getClientIp(request))
                .build());
    }

    @Override
    public Page<OpsAuditLogVO> page(String action, Long operatorId, LocalDate from, LocalDate to,
                                    int pageNum, int pageSize) {
        LocalDate[] range = normalizeRange(from, to);
        int num = Math.max(1, pageNum);
        int size = Math.min(100, Math.max(1, pageSize));
        QueryWrapper query = QueryWrapper.create()
                .ge(OpsAuditLog::getCreateTime, range[0].atStartOfDay())
                .le(OpsAuditLog::getCreateTime, range[1].atTime(LocalTime.MAX))
                .orderBy(OpsAuditLog::getCreateTime, false);
        if (StrUtil.isNotBlank(action)) {
            query.eq(OpsAuditLog::getAction, action.trim());
        }
        if (operatorId != null) {
            query.eq(OpsAuditLog::getOperatorId, operatorId);
        }
        Page<OpsAuditLog> page = opsAuditLogMapper.paginate(Page.of(num, size), query);
        Page<OpsAuditLogVO> voPage = new Page<>(num, size, page.getTotalRow());
        voPage.setRecords(page.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public int purgeExpired() {
        int days = Math.max(1, opsRuntimeSettings.opsAuditRetainDays());
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        return opsAuditLogMapper.deleteByQuery(QueryWrapper.create()
                .lt(OpsAuditLog::getCreateTime, cutoff));
    }

    private String toSafeDetailJson(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) {
            return null;
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : detail.entrySet()) {
            String key = entry.getKey();
            if (key != null && SENSITIVE_KEYS.contains(key.toLowerCase(Locale.ROOT))) {
                safe.put(key, "***");
            } else {
                safe.put(key, entry.getValue());
            }
        }
        String json = JSONUtil.toJsonStr(safe);
        return truncate(json, MAX_DETAIL);
    }

    private LocalDate[] normalizeRange(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(6);
        if (start.isAfter(end)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }
        return new LocalDate[]{start, end};
    }

    private OpsAuditLogVO toVO(OpsAuditLog row) {
        return OpsAuditLogVO.builder()
                .id(row.getId())
                .operatorId(row.getOperatorId())
                .action(row.getAction())
                .resourceType(row.getResourceType())
                .resourceId(row.getResourceId())
                .ip(row.getIp())
                .detailJson(row.getDetailJson())
                .success(row.getSuccess() != null && row.getSuccess() == 1)
                .createTime(row.getCreateTime())
                .build();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
