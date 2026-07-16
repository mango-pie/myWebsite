package com.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.ai.constant.AiUsageSceneConstant;
import com.ai.mapper.AiUsageLogMapper;
import com.ai.model.dto.ops.AiUsageRecord;
import com.ai.model.entity.AiUsageLog;
import com.ai.model.vo.ops.AiUsageLogVO;
import com.ai.model.vo.ops.AiUsageSummaryVO;
import com.ai.service.AiUsageLogService;
import com.ai.setting.runtime.OpsRuntimeSettings;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AiUsageLogServiceImpl implements AiUsageLogService {

    private static final int MAX_ERROR = 512;
    private static final int MAX_SUMMARY = 256;

    @Resource
    private AiUsageLogMapper aiUsageLogMapper;

    @Resource
    private OpsRuntimeSettings opsRuntimeSettings;

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
        });
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
    public AiUsageSummaryVO summary(LocalDate from, LocalDate to) {
        LocalDate[] range = normalizeRange(from, to);
        QueryWrapper query = QueryWrapper.create()
                .ge(AiUsageLog::getCreateTime, range[0].atStartOfDay())
                .le(AiUsageLog::getCreateTime, range[1].atTime(LocalTime.MAX));
        List<AiUsageLog> rows = aiUsageLogMapper.selectListByQuery(query);

        long requestCount = rows.size();
        long successCount = 0;
        long errorCount = 0;
        long tokenSum = 0;
        boolean anyToken = false;
        long latencySum = 0;
        long latencyCount = 0;
        Map<String, Long> byScene = new LinkedHashMap<>();
        Map<String, Long> byModel = new LinkedHashMap<>();

        for (AiUsageLog row : rows) {
            if (AiUsageSceneConstant.STATUS_ERROR.equalsIgnoreCase(row.getStatus())) {
                errorCount++;
            } else {
                successCount++;
            }
            if (row.getTotalTokens() != null) {
                tokenSum += row.getTotalTokens();
                anyToken = true;
            }
            if (row.getResponseTimeMs() != null) {
                latencySum += row.getResponseTimeMs();
                latencyCount++;
            }
            String sceneKey = StrUtil.blankToDefault(row.getScene(), AiUsageSceneConstant.OTHER);
            byScene.merge(sceneKey, 1L, Long::sum);
            String modelKey = StrUtil.blankToDefault(row.getModelName(), "unknown");
            byModel.merge(modelKey, 1L, Long::sum);
        }

        Double avgLatency = latencyCount == 0 ? null : (latencySum * 1.0 / latencyCount);
        return AiUsageSummaryVO.builder()
                .requestCount(requestCount)
                .successCount(successCount)
                .errorCount(errorCount)
                .totalTokens(anyToken ? tokenSum : null)
                .avgLatencyMs(avgLatency)
                .byScene(byScene)
                .byModel(byModel)
                .build();
    }

    @Override
    public int purgeExpired() {
        int days = Math.max(1, opsRuntimeSettings.usageLogRetainDays());
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        return aiUsageLogMapper.deleteByQuery(QueryWrapper.create()
                .lt(AiUsageLog::getCreateTime, cutoff));
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
