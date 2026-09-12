-- 全站设置变更审计（P5）
-- 在核心库 aiscene 中执行。

CREATE TABLE IF NOT EXISTS `site_setting_audit` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `module`        VARCHAR(64)  NOT NULL COMMENT '模块编码',
    `setting_key`   VARCHAR(128) NOT NULL COMMENT '配置键',
    `old_value`     TEXT         NULL COMMENT '脱敏后旧值',
    `new_value`     TEXT         NULL COMMENT '脱敏后新值',
    `operator_id`   BIGINT       NULL COMMENT '操作人',
    `action`        VARCHAR(32)  NOT NULL COMMENT 'UPDATE / RESET',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '时间',
    PRIMARY KEY (`id`),
    KEY `idx_module_time` (`module`, `create_time`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='全站设置变更审计';
