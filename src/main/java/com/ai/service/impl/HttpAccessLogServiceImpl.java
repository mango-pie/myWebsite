package com.ai.service.impl;

import com.ai.config.ConditionalOnModule;

import cn.hutool.core.util.StrUtil;
import com.ai.mapper.ops.HttpAccessLogMapper;
import com.ai.model.entity.HttpAccessLog;
import com.ai.model.vo.ops.HttpAccessLogVO;
import com.ai.service.HttpAccessLogService;
import com.ai.setting.runtime.OpsRuntimeSettings;
import com.ai.utils.BatchDeletes;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@ConditionalOnModule("ops")
@Service
public class HttpAccessLogServiceImpl implements HttpAccessLogService {

    private static final int MAX_PATH = 512;
    private static final int MAX_ERROR = 512;

    @Resource
    private HttpAccessLogMapper httpAccessLogMapper;

    @Resource
    private OpsRuntimeSettings opsRuntimeSettings;

    @Resource(name = "opsLogExecutor")
    private Executor opsLogExecutor;

    @Override
    public void record(String method, String path, Integer status, Long latencyMs,
                       Long userId, String ip, String traceId, String errorSummary) {
        if (!opsRuntimeSettings.httpLogEnabled()) {
            return;
        }
        HttpAccessLog row = HttpAccessLog.builder()
                .method(truncate(method, 16))
                .path(truncate(path, MAX_PATH))
                .status(status)
                .latencyMs(latencyMs)
                .userId(userId)
                .ip(truncate(ip, 64))
                .traceId(truncate(traceId, 64))
                .errorSummary(truncate(errorSummary, MAX_ERROR))
                .createTime(LocalDateTime.now())
                .build();
        CompletableFuture.runAsync(() -> {
            try {
                httpAccessLogMapper.insert(row);
            } catch (Exception e) {
                log.warn("Write http_access_log failed: {}", e.getMessage());
            }
        }, opsLogExecutor);
    }

    @Override
    public Page<HttpAccessLogVO> page(Integer status, String statusClass, String pathPrefix,
                                      LocalDate from, LocalDate to, int pageNum, int pageSize) {
        LocalDate[] range = normalizeRange(from, to);
        int num = Math.max(1, pageNum);
        int size = Math.min(100, Math.max(1, pageSize));
        QueryWrapper query = QueryWrapper.create()
                .ge(HttpAccessLog::getCreateTime, range[0].atStartOfDay())
                .le(HttpAccessLog::getCreateTime, range[1].atTime(LocalTime.MAX))
                .orderBy(HttpAccessLog::getCreateTime, false);
        if (status != null) {
            query.eq(HttpAccessLog::getStatus, status);
        } else if (StrUtil.isNotBlank(statusClass)) {
            String clazz = statusClass.trim().toLowerCase();
            if ("4xx".equals(clazz)) {
                query.ge(HttpAccessLog::getStatus, 400).lt(HttpAccessLog::getStatus, 500);
            } else if ("5xx".equals(clazz)) {
                query.ge(HttpAccessLog::getStatus, 500).lt(HttpAccessLog::getStatus, 600);
            }
        }
        if (StrUtil.isNotBlank(pathPrefix)) {
            // prefix match: path LIKE 'prefix%'
            query.likeRight(HttpAccessLog::getPath, pathPrefix.trim());
        }
        Page<HttpAccessLog> page = httpAccessLogMapper.paginate(Page.of(num, size), query);
        Page<HttpAccessLogVO> voPage = new Page<>(num, size, page.getTotalRow());
        voPage.setRecords(page.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public int purgeExpired() {
        int days = Math.max(1, opsRuntimeSettings.httpLogRetainDays());
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        return BatchDeletes.purge(httpAccessLogMapper,
                () -> QueryWrapper.create().lt(HttpAccessLog::getCreateTime, cutoff));
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

    private HttpAccessLogVO toVO(HttpAccessLog row) {
        return HttpAccessLogVO.builder()
                .id(row.getId())
                .method(row.getMethod())
                .path(row.getPath())
                .status(row.getStatus())
                .latencyMs(row.getLatencyMs())
                .userId(row.getUserId())
                .ip(row.getIp())
                .traceId(row.getTraceId())
                .errorSummary(row.getErrorSummary())
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
