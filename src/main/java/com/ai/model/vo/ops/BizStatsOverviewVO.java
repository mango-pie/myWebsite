package com.ai.model.vo.ops;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BizStatsOverviewVO {
    /** 白名单 metric → 区间合计（缺省 0） */
    private Map<String, Long> totals;
}
