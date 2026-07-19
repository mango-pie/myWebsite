package com.ai.service.impl;

import com.ai.config.ConditionalOnModule;

import cn.hutool.core.util.StrUtil;
import com.ai.constant.BizStatMetricConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.mapper.ops.BizStatDailyMapper;
import com.ai.model.entity.BizStatDaily;
import com.ai.model.vo.ops.BizStatSeriesPointVO;
import com.ai.model.vo.ops.BizStatsOverviewVO;
import com.ai.service.BizStatDailyService;
import com.ai.setting.runtime.OpsRuntimeSettings;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@ConditionalOnModule("ops")
@Service
public class BizStatDailyServiceImpl implements BizStatDailyService {

    @Resource
    private BizStatDailyMapper bizStatDailyMapper;

    @Resource
    private OpsRuntimeSettings opsRuntimeSettings;

    @Override
    public void increment(String metric, long delta) {
        if (!opsRuntimeSettings.bizStatsEnabled() || delta == 0) {
            return;
        }
        if (!BizStatMetricConstant.isAllowed(metric)) {
            log.warn("Ignore unknown biz metric: {}", metric);
            return;
        }
        LocalDate today = LocalDate.now();
        CompletableFuture.runAsync(() -> {
            try {
                bizStatDailyMapper.upsertIncrement(
                        today, metric, BizStatMetricConstant.DIM_TOTAL, delta);
            } catch (Exception e) {
                log.warn("Write biz_stat_daily failed: {}", e.getMessage());
            }
        });
    }

    @Override
    public BizStatsOverviewVO overview(LocalDate from, LocalDate to) {
        LocalDate[] range = normalizeRange(from, to);
        QueryWrapper query = QueryWrapper.create()
                .ge(BizStatDaily::getStatDate, range[0])
                .le(BizStatDaily::getStatDate, range[1])
                .eq(BizStatDaily::getDim, BizStatMetricConstant.DIM_TOTAL);
        List<BizStatDaily> rows = bizStatDailyMapper.selectListByQuery(query);

        Map<String, Long> totals = new LinkedHashMap<>();
        for (String metric : BizStatMetricConstant.ALL) {
            totals.put(metric, 0L);
        }
        for (BizStatDaily row : rows) {
            if (row.getMetric() == null) {
                continue;
            }
            long value = row.getValue() == null ? 0L : row.getValue();
            totals.merge(row.getMetric(), value, Long::sum);
        }
        return BizStatsOverviewVO.builder().totals(totals).build();
    }

    @Override
    public List<BizStatSeriesPointVO> series(String metric, LocalDate from, LocalDate to) {
        if (StrUtil.isBlank(metric) || !BizStatMetricConstant.isAllowed(metric.trim())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "非法 metric，须在白名单内");
        }
        String metricKey = metric.trim();
        LocalDate[] range = normalizeRange(from, to);
        QueryWrapper query = QueryWrapper.create()
                .eq(BizStatDaily::getMetric, metricKey)
                .eq(BizStatDaily::getDim, BizStatMetricConstant.DIM_TOTAL)
                .ge(BizStatDaily::getStatDate, range[0])
                .le(BizStatDaily::getStatDate, range[1]);
        List<BizStatDaily> rows = bizStatDailyMapper.selectListByQuery(query);
        Map<LocalDate, Long> byDate = new LinkedHashMap<>();
        for (BizStatDaily row : rows) {
            long value = row.getValue() == null ? 0L : row.getValue();
            byDate.merge(row.getStatDate(), value, Long::sum);
        }

        List<BizStatSeriesPointVO> points = new ArrayList<>();
        for (LocalDate d = range[0]; !d.isAfter(range[1]); d = d.plusDays(1)) {
            points.add(BizStatSeriesPointVO.builder()
                    .date(d)
                    .value(byDate.getOrDefault(d, 0L))
                    .build());
        }
        return points;
    }

    @Override
    public int purgeExpired() {
        LocalDate cutoff = LocalDate.now().minusDays(BizStatMetricConstant.RETAIN_DAYS);
        return bizStatDailyMapper.deleteByQuery(QueryWrapper.create()
                .lt(BizStatDaily::getStatDate, cutoff));
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
}
