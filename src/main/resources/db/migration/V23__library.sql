-- 资料库（资料储藏室）：MinIO 对象 + 元数据；软删除进回收站保留 30 天
CREATE TABLE IF NOT EXISTS library_file (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    file_name VARCHAR(512) NOT NULL,
    ext VARCHAR(32) NOT NULL DEFAULT '' COMMENT '小写扩展名（无点）',
    category VARCHAR(16) NOT NULL DEFAULT 'OTHER' COMMENT 'DOCUMENT/IMAGE/AUDIO/VIDEO/CODE/ARCHIVE/OTHER',
    size BIGINT NOT NULL DEFAULT 0,
    sha256 CHAR(64) NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    upload_status VARCHAR(16) NOT NULL DEFAULT 'DONE' COMMENT 'UPLOADING/DONE（direct 直传中不进列表）',
    ai_status VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/PENDING/DONE/FAILED',
    ai_summary TEXT DEFAULT NULL,
    ai_error TEXT DEFAULT NULL,
    tag_ids TEXT COMMENT '标签 id JSON 数组',
    collection_ids TEXT COMMENT '合集 id JSON 数组',
    last_opened_at DATETIME DEFAULT NULL COMMENT '最近取出',
    open_count INT NOT NULL DEFAULT 0,
    deleted_at DATE DEFAULT NULL COMMENT '进回收站日期，保留 30 天',
    delete_time DATETIME DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_library_file_user (user_id, upload_status, deleted_at),
    KEY idx_library_file_sha (user_id, sha256),
    KEY idx_library_file_ai (ai_status),
    KEY idx_library_file_recycle (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资料库文件';

CREATE TABLE IF NOT EXISTS library_tag (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    color VARCHAR(16) NOT NULL DEFAULT 'sakura' COMMENT 'sakura/violet/lilac/blue/mint/sun',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_library_tag_user_name (user_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资料库标签';

CREATE TABLE IF NOT EXISTS library_collection (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    color VARCHAR(16) NOT NULL DEFAULT 'blue',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_library_collection_user_name (user_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资料库合集/书脊';
