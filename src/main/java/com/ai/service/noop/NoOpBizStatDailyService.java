package com.ai.service.noop;

import com.ai.model.vo.ops.BizStatSeriesPointVO;
import com.ai.model.vo.ops.BizStatsOverviewVO;
import com.ai.service.BizStatDailyService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Primary
@Service
@ConditionalOnProperty(name = "app.modules.ops", havingValue = "false")
public class NoOpBizStatDailyService implements BizStatDailyService {

    @Override
    public void increment(String metric, long delta) {
    }

    @Override
    public BizStatsOverviewVO overview(LocalDate from, LocalDate to) {
        return new BizStatsOverviewVO();
    }

    @Override
    public List<BizStatSeriesPointVO> series(String metric, LocalDate from, LocalDate to) {
        return List.of();
    }

    @Override
    public int purgeExpired() {
        return 0;
    }
}
