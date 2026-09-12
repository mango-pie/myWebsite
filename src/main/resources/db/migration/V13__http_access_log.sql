-- HTTP 访问 / 错误日志（运维可观测 Phase D）
-- 在核心库 aiscene 中执行。

CREATE TABLE IF NOT EXISTS `http_access_log` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `method`         VARCHAR(16)  NULL COMMENT 'HTTP 方法',
    `path`           VARCHAR(512) NULL COMMENT '请求路径（无 query）',
    `status`         INT          NULL COMMENT 'HTTP 状态码',
    `latency_ms`     BIGINT       NULL COMMENT '耗时 ms',
    `user_id`        BIGINT       NULL COMMENT '登录用户',
    `ip`             VARCHAR(64)  NULL COMMENT '客户端 IP',
    `trace_id`       VARCHAR(64)  NULL COMMENT '追踪 ID',
    `error_summary`  VARCHAR(512) NULL COMMENT '错误摘要',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_status_time` (`status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='HTTP 访问日志';
