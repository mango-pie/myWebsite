CREATE TABLE IF NOT EXISTS knowledge_reading_job (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED',
    progress VARCHAR(32) NOT NULL DEFAULT 'QUEUED' COMMENT 'QUEUED/READING/DISTILLING/DONE/ERROR',
    request_json MEDIUMTEXT NOT NULL COMMENT 'urls/agentQuery/distillPrompt/tags/sourceType',
    result_json MEDIUMTEXT DEFAULT NULL COMMENT '合并精读结果快照',
    note_id BIGINT DEFAULT NULL,
    error_msg TEXT DEFAULT NULL,
    started_at DATETIME DEFAULT NULL,
    finished_at DATETIME DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_delete TINYINT NOT NULL DEFAULT 0,
    KEY idx_reading_job_status (status, is_delete),
    KEY idx_reading_job_user_time (user_id, create_time),
    KEY idx_reading_job_note (note_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='精读合蒸异步任务';
