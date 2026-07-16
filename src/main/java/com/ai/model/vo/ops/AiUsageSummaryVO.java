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
public class AiUsageSummaryVO {
    private long requestCount;
    private long successCount;
    private long errorCount;
    /** 有值记录的 Token 合计；全部为空时为 null */
    private Long totalTokens;
    private Double avgLatencyMs;
    private Map<String, Long> byScene;
    private Map<String, Long> byModel;
}
