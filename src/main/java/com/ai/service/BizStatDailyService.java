package com.ai.service;

import com.ai.model.vo.ops.BizStatSeriesPointVO;
import com.ai.model.vo.ops.BizStatsOverviewVO;

import java.time.LocalDate;
import java.util.List;

public interface BizStatDailyService {

    void increment(String metric, long delta);

    BizStatsOverviewVO overview(LocalDate from, LocalDate to);

    List<BizStatSeriesPointVO> series(String metric, LocalDate from, LocalDate to);

    int purgeExpired();
}
