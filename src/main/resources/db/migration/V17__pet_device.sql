-- EchoBot / 桌宠设备令牌（长期绑定，替代 Cookie Session）

CREATE TABLE IF NOT EXISTS pet_device (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '设备记录ID',
    user_id       BIGINT       NOT NULL COMMENT '绑定用户',
    token_hash    CHAR(64)     NOT NULL COMMENT 'SHA-256(hex) of raw token',
    token_prefix  VARCHAR(16)  NOT NULL COMMENT '明文前缀，便于识别',
    device_name   VARCHAR(100) NOT NULL DEFAULT 'EchoBot' COMMENT '设备显示名',
    client_info   VARCHAR(255) NULL COMMENT '客户端信息（OS/版本等）',
    last_used_at  DATETIME     NULL COMMENT '最近使用时间',
    revoked_at    DATETIME     NULL COMMENT '撤销时间（软吊销）',
    created_time  DATETIME     NOT NULL COMMENT '创建时间',
    updated_time  DATETIME     NOT NULL COMMENT '更新时间',
    UNIQUE KEY uk_token_hash (token_hash),
    KEY idx_user_revoked (user_id, revoked_at),
    KEY idx_user_created (user_id, created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='桌宠设备令牌';
