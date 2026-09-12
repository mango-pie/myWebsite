package com.ai.service.impl;

import com.ai.config.ConditionalOnModule;

import cn.hutool.core.util.StrUtil;
import com.ai.constant.AiUsageSceneConstant;
import com.ai.mapper.ops.AiUsageLogMapper;
import com.ai.model.dto.ops.AiUsageAggRow;
import com.ai.model.dto.ops.AiUsageRecord;
import com.ai.model.entity.AiUsageLog;
import com.ai.model.vo.ops.AiUsageLogVO;
import com.ai.model.vo.ops.AiUsageMonthlyVO;
import com.ai.model.vo.ops.AiUsageSummaryVO;
import com.ai.service.AiUsageLogService;
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
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@ConditionalOnModule("ops")
@Service
public class AiUsageLogServiceImpl implements AiUsageLogService {

    private static final int MAX_ERROR = 512;
    private static final int MAX_SUMMARY = 256;

    @Resource
    private AiUsageLogMapper aiUsageLogMapper;

    @Resource
    private OpsRuntimeSettings opsRuntimeSettings;

    @Resource(name = "opsLogExecutor")
    private Executor opsLogExecutor;

    @Override
    public void record(AiUsageRecord record) {
        if (record == null || !opsRuntimeSettings.usageLogEnabled()) {
            return;
        }
        AiUsageLog row = AiUsageLog.builder()
                .userId(record.getUserId())
                .scene(StrUtil.blankToDefault(record.getScene(), AiUsageSceneConstant.OTHER))
                .conversationId(record.getConversationId())
                .modelName(truncate(record.getModelName(), 128))
                .promptTokens(record.getPromptTokens())
                .completionTokens(record.getCompletionTokens())
                .totalTokens(resolveTotal(record))
                .responseTimeMs(record.getResponseTimeMs())
                .status(StrUtil.blankToDefault(record.getStatus(), AiUsageSceneConstant.STATUS_SUCCESS))
                .errorMessage(truncate(record.getErrorMessage(), MAX_ERROR))
                .requestSummary(truncate(record.getRequestSummary(), MAX_SUMMARY))
                .createTime(LocalDateTime.now())
                .build();
        CompletableFuture.runAsync(() -> {
            try {
                aiUsageLogMapper.insert(row);
            } catch (Exception e) {
                log.warn("Write ai_usage_log failed: {}", e.getMessage());
            }
        }, opsLogExecutor);
    }

    @Override
    public Page<AiUsageLogVO> pageLogs(String scene, Long userId, LocalDate from, LocalDate to,
                                       int pageNum, int pageSize) {
        LocalDate[] range = normalizeRange(from, to);
        int num = Math.max(1, pageNum);
        int size = Math.min(100, Math.max(1, pageSize));
        QueryWrapper query = QueryWrapper.create()
                .ge(AiUsageLog::getCreateTime, range[0].atStartOfDay())
                .le(AiUsageLog::getCreateTime, range[1].atTime(LocalTime.MAX))
                .orderBy(AiUsageLog::getCreateTime, false);
        if (StrUtil.isNotBlank(scene)) {
            query.eq(AiUsageLog::getScene, scene.trim());
        }
        if (userId != null) {
            query.eq(AiUsageLog::getUserId, userId);
        }
        Page<AiUsageLog> page = aiUsageLogMapper.paginate(Page.of(num, size), query);
        Page<AiUsageLogVO> voPage = new Page<>(num, size, page.getTotalRow());
        voPage.setRecords(page.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public AiUsageMonthlyVO monthly(LocalDate month) {
        YearMonth ym = YearMonth.from(month == null ? LocalDate.now() : month);
        LocalDateTime start = ym.atDay(1).atStartOfDay();
        LocalDateTime end = ym.atEndOfMonth().atTime(LocalTime.MAX);

        // 聚合下推到 SQL：整月明细拉进 JVM 内存聚合在日志表增长后会 OOM/超时
        AiUsageMonthlyVO vo = new AiUsageMonthlyVO();
        vo.setMonth(ym.toString());

        long requestCount = 0;
        long errorCount = 0;
        long tokenSum = 0;
        boolean anyToken = false;
        List<AiUsageAggRow> dayRows = aiUsageLogMapper.selectDailyAgg(start, end);
        List<AiUsageMonthlyVO.DayStat> days = new ArrayList<>(dayRows.size());
        for (AiUsageAggRow row : dayRows) {
            long dayRequests = row.getRequestCount() == null ? 0 : row.getRequestCount();
            long dayErrors = row.getErrorCount() == null ? 0 : row.getErrorCount();
            requestCount += dayRequests;
            errorCount += dayErrors;
            AiUsageMonthlyVO.DayStat stat = AiUsageMonthlyVO.DayStat.builder()
                    .date(row.getAggDate())
                    .requestCount(dayRequests)
                    .successCount(dayRequests - dayErrors)
                    .errorCount(dayErrors)
                    .totalTokens(row.getTotalTokens())
                    .build();
            if (row.getLatencyCount() != null && row.getLatencyCount() > 0) {
                stat.setAvgLatencyMs(row.getLatencySum() * 1.0 / row.getLatencyCount());
            }
            days.add(stat);
            if (row.getTotalTokens() != null) {
                tokenSum += row.getTotalTokens();
                anyToken = true;
            }
        }
        vo.setRequestCount(requestCount);
        vo.setErrorCount(errorCount);
        vo.setSuccessCount(requestCount - errorCount);
        vo.setTotalTokens(anyToken ? tokenSum : null);
        vo.setByScene(toCountMap(aiUsageLogMapper.selectSceneAgg(start, end)));
        vo.setByModel(toModelStats(aiUsageLogMapper.selectModelAgg(start, end)));
        vo.setDays(days);
        return vo;
    }

    @Override
    public AiUsageSummaryVO summary(LocalDate from, LocalDate to) {
        LocalDate[] range = normalizeRange(from, to);
        LocalDateTime start = range[0].atStartOfDay();
        LocalDateTime end = range[1].atTime(LocalTime.MAX);

        AiUsageAggRow global = aiUsageLogMapper.selectGlobalAgg(start, end);
        long requestCount = global.getRequestCount() == null ? 0 : global.getRequestCount();
        long errorCount = global.getErrorCount() == null ? 0 : global.getErrorCount();
        Double avgLatency = (global.getLatencyCount() == null || global.getLatencyCount() == 0)
                ? null
                : global.getLatencySum() * 1.0 / global.getLatencyCount();
        return AiUsageSummaryVO.builder()
                .requestCount(requestCount)
                .successCount(requestCount - errorCount)
                .errorCount(errorCount)
                .totalTokens(global.getTotalTokens())
                .avgLatencyMs(avgLatency)
                .byScene(toCountMap(aiUsageLogMapper.selectSceneAgg(start, end)))
                .byModel(toCountMap(aiUsageLogMapper.selectModelAgg(start, end)))
                .build();
    }

    private Map<String, Long> toCountMap(List<AiUsageAggRow> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (AiUsageAggRow row : rows) {
            map.put(row.getAggKey(), row.getRequestCount() == null ? 0L : row.getRequestCount());
        }
        return map;
    }

    private Map<String, AiUsageMonthlyVO.ModelStat> toModelStats(List<AiUsageAggRow> rows) {
        Map<String, AiUsageMonthlyVO.ModelStat> map = new LinkedHashMap<>();
        for (AiUsageAggRow row : rows) {
            AiUsageMonthlyVO.ModelStat stat = AiUsageMonthlyVO.ModelStat.builder()
                    .requestCount(row.getRequestCount() == null ? 0 : row.getRequestCount())
                    .totalTokens(row.getTotalTokens() == null ? 0 : row.getTotalTokens())
                    .build();
            map.put(row.getAggKey(), stat);
        }
        return map;
    }

    @Override
    public int purgeExpired() {
        int days = Math.max(1, opsRuntimeSettings.usageLogRetainDays());
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        return BatchDeletes.purge(aiUsageLogMapper,
                () -> QueryWrapper.create().lt(AiUsageLog::getCreateTime, cutoff));
    }

    private Integer resolveTotal(AiUsageRecord record) {
        if (record.getTotalTokens() != null) {
            return record.getTotalTokens();
        }
        if (record.getPromptTokens() != null && record.getCompletionTokens() != null) {
            return record.getPromptTokens() + record.getCompletionTokens();
        }
        return null;
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

    private AiUsageLogVO toVO(AiUsageLog row) {
        return AiUsageLogVO.builder()
                .id(row.getId())
                .userId(row.getUserId())
                .scene(row.getScene())
                .conversationId(row.getConversationId())
                .modelName(row.getModelName())
                .promptTokens(row.getPromptTokens())
                .completionTokens(row.getCompletionTokens())
                .totalTokens(row.getTotalTokens())
                .responseTimeMs(row.getResponseTimeMs())
                .status(row.getStatus())
                .errorMessage(row.getErrorMessage())
                .requestSummary(row.getRequestSummary())
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
