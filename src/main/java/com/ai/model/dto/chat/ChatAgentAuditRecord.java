package com.ai.model.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 工具审计写入载体（模式同 AiUsageRecord）：网关执行完一次工具调用后组装。
 * toolArgs 为模型产出的原始参数 JSON，由服务端截断落库，不存完整正文。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatAgentAuditRecord {

    private Long userId;

    private Long conversationId;

    private String toolName;

    private String toolArgs;

    private boolean success;

    private String resultSummary;
}
