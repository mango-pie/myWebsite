-- Agent 工具审计日志（Ask/Agent 双模式设计 §8.2，L2 确认流二期配套）
-- 记录每次经 AgentToolGateway 的工具调用，用于排查「AI 误创建」类问题；
-- tool_args 截断存储，不落完整博客正文。

CREATE TABLE IF NOT EXISTS `chat_agent_audit` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`         BIGINT       NOT NULL COMMENT '用户 ID',
    `conversation_id` BIGINT       NULL COMMENT '会话 ID',
    `tool_name`       VARCHAR(64)  NOT NULL COMMENT '工具名',
    `tool_args`       TEXT         NULL COMMENT '工具参数 JSON（截断存储）',
    `success`         TINYINT      NOT NULL DEFAULT 1 COMMENT '1 成功 / 0 失败',
    `result_summary`  VARCHAR(512) NULL COMMENT '结果摘要（截断，不存完整正文）',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_chat_agent_audit_create_time` (`create_time`),
    KEY `idx_chat_agent_audit_user_time` (`user_id`, `create_time`),
    KEY `idx_chat_agent_audit_tool_time` (`tool_name`, `create_time`),
    KEY `idx_chat_agent_audit_conversation` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 工具审计日志';
