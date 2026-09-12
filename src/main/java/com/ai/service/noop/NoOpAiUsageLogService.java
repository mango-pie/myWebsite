package com.ai.service.noop;

import com.ai.model.dto.ops.AiUsageRecord;
import com.ai.model.vo.ops.AiUsageLogVO;
import com.ai.model.vo.ops.AiUsageMonthlyVO;
import com.ai.model.vo.ops.AiUsageSummaryVO;
import com.ai.service.AiUsageLogService;
import com.mybatisflex.core.paginate.Page;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Primary
@Service
@ConditionalOnProperty(name = "app.modules.ops", havingValue = "false")
public class NoOpAiUsageLogService implements AiUsageLogService {

    @Override
    public void record(AiUsageRecord record) {
    }

    @Override
    public Page<AiUsageLogVO> pageLogs(String scene, Long userId, LocalDate from, LocalDate to,
                                       int pageNum, int pageSize) {
        return new Page<>(pageNum, pageSize, 0);
    }

    @Override
    public AiUsageSummaryVO summary(LocalDate from, LocalDate to) {
        return new AiUsageSummaryVO();
    }

    @Override
    public AiUsageMonthlyVO monthly(LocalDate month) {
        return new AiUsageMonthlyVO();
    }

    @Override
    public int purgeExpired() {
        return 0;
    }
}
