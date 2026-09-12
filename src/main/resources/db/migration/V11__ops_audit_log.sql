-- 通用操作审计（运维可观测 Phase B）
-- 与 site_setting_audit 并存；在核心库 aiscene 中执行。

CREATE TABLE IF NOT EXISTS `ops_audit_log` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `operator_id`    BIGINT       NULL COMMENT '操作人，匿名失败可空',
    `action`         VARCHAR(64)  NOT NULL COMMENT '动作码',
    `resource_type`  VARCHAR(64)  NULL COMMENT '资源类型',
    `resource_id`    VARCHAR(64)  NULL COMMENT '资源 ID',
    `ip`             VARCHAR(64)  NULL COMMENT '客户端 IP',
    `detail_json`    TEXT         NULL COMMENT '脱敏后 JSON',
    `success`        TINYINT      NOT NULL DEFAULT 1 COMMENT '1 成功 / 0 失败',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_action_time` (`action`, `create_time`),
    KEY `idx_operator_time` (`operator_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通用操作审计';
