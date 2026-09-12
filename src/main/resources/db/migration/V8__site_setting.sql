-- 全站设置中心（Site Settings）P0
-- 在核心库 aiscene 中执行；导入顺序见 docs/DEPLOY_RECORD.md / 部署文档。

CREATE TABLE IF NOT EXISTS `site_setting` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `module`        VARCHAR(64)  NOT NULL COMMENT '模块编码',
    `setting_key`   VARCHAR(128) NOT NULL COMMENT '配置键',
    `setting_value` TEXT         NULL COMMENT '配置值（序列化文本）',
    `value_type`    VARCHAR(32)  NOT NULL DEFAULT 'string' COMMENT 'string/int/long/bool/double/json',
    `sensitive`     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否敏感：0否 1是',
    `updated_by`    BIGINT       NULL COMMENT '最后修改人',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_delete`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_module_key` (`module`, `setting_key`),
    KEY `idx_module` (`module`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='全站系统设置';

-- P5 审计表见 site_setting_audit_schema.sql
