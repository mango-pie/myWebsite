-- EchoBot 桌宠设备绑定：明文 token 仅在绑定时返回一次，库存 SHA-256 哈希
CREATE TABLE IF NOT EXISTS pet_device (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    device_name VARCHAR(128) NOT NULL,
    client_info TEXT COMMENT '设备环境信息 JSON',
    token_hash CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/REVOKED',
    bound_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at DATETIME DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_pet_device_user (user_id),
    KEY idx_pet_device_token (token_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='桌宠设备绑定';
