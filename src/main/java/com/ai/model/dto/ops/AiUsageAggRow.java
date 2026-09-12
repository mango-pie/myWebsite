package com.ai.model.dto.ops;

import lombok.Data;

import java.time.LocalDate;

/**
 * ai_usage_log SQL 聚合投影行：按天 / 按场景 / 按模型聚合共用。
 * aggDate 用于按天分组结果，aggKey 用于 scene / model_name 分组结果。
 */
@Data
public class AiUsageAggRow {

    private LocalDate aggDate;

    private String aggKey;

    private Long requestCount;

    private Long errorCount;

    /** SUM(total_tokens)：全部为 NULL 时为 NULL（对应 VO 的 anyToken 语义） */
    private Long totalTokens;

    private Long latencySum;

    private Long latencyCount;
}
