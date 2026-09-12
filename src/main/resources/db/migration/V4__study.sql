-- 学习模块 DDL（study_module_design.md §5）

CREATE TABLE IF NOT EXISTS study_list (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '清单ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    name VARCHAR(50) NOT NULL COMMENT '清单名称',
    color VARCHAR(20) NULL COMMENT '标识色',
    icon VARCHAR(50) NULL COMMENT '图标',
    list_type TINYINT DEFAULT 0 COMMENT '0普通 1系统收集箱',
    sort_order INT DEFAULT 0 COMMENT '排序',
    task_count INT DEFAULT 0 COMMENT '未完成任务数',
    status TINYINT DEFAULT 1 COMMENT '0禁用 1启用',
    extend_info JSON NULL COMMENT '扩展信息',
    created_time DATETIME NOT NULL COMMENT '创建时间',
    updated_time DATETIME NOT NULL COMMENT '更新时间',
    deleted_time DATETIME NULL COMMENT '软删除时间',
    KEY idx_user_id (user_id),
    KEY idx_user_sort (user_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习清单表';

CREATE TABLE IF NOT EXISTS study_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '任务ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    list_id BIGINT NOT NULL COMMENT '清单ID',
    title VARCHAR(200) NOT NULL COMMENT '标题',
    content TEXT NULL COMMENT '备注',
    status TINYINT DEFAULT 0 COMMENT '0未完成 1完成 2放弃',
    priority TINYINT DEFAULT 0 COMMENT '优先级0-3',
    due_date DATETIME NULL COMMENT '截止时间',
    is_today TINYINT DEFAULT 0 COMMENT '是否今日要做',
    sort_order INT DEFAULT 0 COMMENT '排序',
    source_type TINYINT DEFAULT 0 COMMENT '来源类型',
    source_id BIGINT NULL COMMENT '来源ID',
    completed_time DATETIME NULL COMMENT '完成时间',
    extend_info JSON NULL COMMENT '扩展信息',
    created_time DATETIME NOT NULL COMMENT '创建时间',
    updated_time DATETIME NOT NULL COMMENT '更新时间',
    deleted_time DATETIME NULL COMMENT '软删除时间',
    KEY idx_user_list (user_id, list_id, status, sort_order),
    KEY idx_user_due (user_id, due_date, status),
    KEY idx_user_today (user_id, is_today, status),
    KEY idx_user_completed (user_id, completed_time),
    KEY idx_source (source_type, source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习任务表';

CREATE TABLE IF NOT EXISTS study_task_checklist (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '检查项ID',
    task_id BIGINT NOT NULL COMMENT '父任务ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    title VARCHAR(200) NOT NULL COMMENT '标题',
    done TINYINT DEFAULT 0 COMMENT '0未完成 1完成',
    sort_order INT DEFAULT 0 COMMENT '排序',
    created_time DATETIME NOT NULL COMMENT '创建时间',
    updated_time DATETIME NOT NULL COMMENT '更新时间',
    deleted_time DATETIME NULL COMMENT '软删除时间',
    KEY idx_task_id (task_id, sort_order),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务检查项表';

CREATE TABLE IF NOT EXISTS study_focus_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '会话ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    task_id BIGINT NULL COMMENT '关联任务ID',
    focus_type TINYINT DEFAULT 0 COMMENT '0工作 1短休息 2长休息',
    planned_minutes INT NOT NULL COMMENT '计划分钟',
    actual_seconds INT DEFAULT 0 COMMENT '实际秒数',
    status TINYINT DEFAULT 0 COMMENT '0进行中 1完成 2放弃 3暂停',
    started_time DATETIME NOT NULL COMMENT '开始时间',
    ended_time DATETIME NULL COMMENT '结束时间',
    pause_total_seconds INT DEFAULT 0 COMMENT '暂停累计秒',
    extend_info JSON NULL COMMENT '扩展信息',
    created_time DATETIME NOT NULL COMMENT '创建时间',
    updated_time DATETIME NOT NULL COMMENT '更新时间',
    KEY idx_user_status (user_id, status),
    KEY idx_user_started (user_id, started_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='专注会话表';

CREATE TABLE IF NOT EXISTS study_habit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '习惯ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    title VARCHAR(100) NOT NULL COMMENT '标题',
    description VARCHAR(300) NULL COMMENT '描述',
    icon VARCHAR(50) NULL COMMENT '图标',
    color VARCHAR(20) NULL COMMENT '颜色',
    target_days_per_week TINYINT DEFAULT 7 COMMENT '每周目标天数',
    streak_count INT DEFAULT 0 COMMENT '当前连续天数',
    best_streak INT DEFAULT 0 COMMENT '最佳连续',
    last_check_date DATE NULL COMMENT '最后打卡日期',
    sort_order INT DEFAULT 0 COMMENT '排序',
    status TINYINT DEFAULT 1 COMMENT '0停用 1启用',
    created_time DATETIME NOT NULL COMMENT '创建时间',
    updated_time DATETIME NOT NULL COMMENT '更新时间',
    deleted_time DATETIME NULL COMMENT '软删除时间',
    KEY idx_user_sort (user_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习习惯表';

CREATE TABLE IF NOT EXISTS study_habit_check_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',
    habit_id BIGINT NOT NULL COMMENT '习惯ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    check_date DATE NOT NULL COMMENT '打卡日期',
    created_time DATETIME NOT NULL COMMENT '创建时间',
    UNIQUE KEY uk_habit_date (habit_id, check_date),
    KEY idx_user_date (user_id, check_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='习惯打卡记录表';
