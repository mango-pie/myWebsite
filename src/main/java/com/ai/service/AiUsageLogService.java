package com.ai.service;

import com.ai.model.dto.ops.AiUsageRecord;
import com.ai.model.vo.ops.AiUsageLogVO;
import com.ai.model.vo.ops.AiUsageSummaryVO;
import com.mybatisflex.core.paginate.Page;

import java.time.LocalDate;

public interface AiUsageLogService {

    void record(AiUsageRecord record);

    Page<AiUsageLogVO> pageLogs(String scene, Long userId, LocalDate from, LocalDate to, int pageNum, int pageSize);

    AiUsageSummaryVO summary(LocalDate from, LocalDate to);

    int purgeExpired();
}
