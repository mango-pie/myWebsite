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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("ai_usage_log")
public class AiUsageLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column("user_id")
    private Long userId;

    @Column("scene")
    private String scene;

    @Column("conversation_id")
    private Long conversationId;

    @Column("model_name")
    private String modelName;

    @Column("prompt_tokens")
    private Integer promptTokens;

    @Column("completion_tokens")
    private Integer completionTokens;

    @Column("total_tokens")
    private Integer totalTokens;

    @Column("response_time_ms")
    private Long responseTimeMs;

    @Column("status")
    private String status;

    @Column("error_message")
    private String errorMessage;

    @Column("request_summary")
    private String requestSummary;

    @Column("create_time")
    private LocalDateTime createTime;
}
