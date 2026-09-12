-- AI / Token 用量日志（运维可观测 Phase A）
-- 在核心库 aiscene 中执行。

CREATE TABLE IF NOT EXISTS `ai_usage_log` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`            BIGINT       NULL COMMENT '用户 ID，系统任务可空',
    `scene`              VARCHAR(64)  NOT NULL COMMENT '场景',
    `conversation_id`    BIGINT       NULL COMMENT '会话 / 应用 ID',
    `model_name`         VARCHAR(128) NULL COMMENT '模型名',
    `prompt_tokens`      INT          NULL COMMENT '输入 Token',
    `completion_tokens`  INT          NULL COMMENT '输出 Token',
    `total_tokens`       INT          NULL COMMENT '总 Token',
    `response_time_ms`   BIGINT       NULL COMMENT '响应耗时 ms',
    `status`             VARCHAR(32)  NOT NULL COMMENT 'success / error',
    `error_message`      VARCHAR(512) NULL COMMENT '截断错误信息',
    `request_summary`    VARCHAR(256) NULL COMMENT '请求摘要，非全文 Prompt',
    `create_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_scene_time` (`scene`, `create_time`),
    KEY `idx_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 调用用量日志';
