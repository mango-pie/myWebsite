package com.ai.model.vo.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Agent 工具审计日志展示 VO（模式同 OpsAuditLogVO）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatAgentAuditVO {

    private Long id;

    private Long userId;

    private Long conversationId;

    private String toolName;

    private String toolArgs;

    private Boolean success;

    private String resultSummary;

    private LocalDateTime createTime;
}
