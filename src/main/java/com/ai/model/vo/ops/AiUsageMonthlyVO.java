package com.ai.model.vo.ops;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 用量月度视图：按天序列 + 场景/模型聚合（含 token，供前端按模型单价估算成本）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiUsageMonthlyVO {

    /** YYYY-MM */
    private String month;
    private long requestCount;
    private long successCount;
    private long errorCount;
    private Long totalTokens;
    private Map<String, Long> byScene = new LinkedHashMap<>();
    private Map<String, ModelStat> byModel = new LinkedHashMap<>();
    private List<DayStat> days = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelStat {
        private long requestCount;
        private long totalTokens;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DayStat {
        private LocalDate date;
        private long requestCount;
        private long successCount;
        private long errorCount;
        private Long totalTokens;
        private Double avgLatencyMs;
    }
}
