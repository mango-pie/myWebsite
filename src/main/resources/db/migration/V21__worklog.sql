-- 工作日志云同步（前端 /worklog/**，本地 IndexedDB 为主、云端为备份）
CREATE TABLE IF NOT EXISTS work_log (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    work_date DATE NOT NULL COMMENT '工作日期 yyyy-MM-dd',
    title VARCHAR(255) DEFAULT NULL,
    done TEXT COMMENT '已完成',
    problem TEXT COMMENT '遇到的问题',
    summary TEXT COMMENT '总结',
    plan TEXT COMMENT '明日计划',
    tags TEXT COMMENT '标签 JSON 数组，如 ["java","refactor"]',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_work_log_user_date (user_id, work_date),
    KEY idx_work_log_user_time (user_id, work_date DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作日志';
