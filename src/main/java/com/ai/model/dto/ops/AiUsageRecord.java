package com.ai.model.dto.ops;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiUsageRecord {
    private Long userId;
    private String scene;
    private Long conversationId;
    private String modelName;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Long responseTimeMs;
    private String status;
    private String errorMessage;
    private String requestSummary;
}
