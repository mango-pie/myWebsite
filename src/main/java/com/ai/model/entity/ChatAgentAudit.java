package com.ai.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Agent 工具审计日志（设计文档 §8.2 chat_agent_audit）：
 * 记录每次经 AgentToolGateway 执行的工具调用，用于排查「AI 误创建」类问题。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("chat_agent_audit")
public class ChatAgentAudit implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column("user_id")
    private Long userId;

    @Column("conversation_id")
    private Long conversationId;

    @Column("tool_name")
    private String toolName;

    @Column("tool_args")
    private String toolArgs;

    @Column("success")
    private Integer success;

    @Column("result_summary")
    private String resultSummary;

    @Column("create_time")
    private LocalDateTime createTime;
}
