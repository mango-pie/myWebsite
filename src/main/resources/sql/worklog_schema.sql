-- Manual apply if Flyway not used. Same as V18__worklog.sql

CREATE TABLE IF NOT EXISTS worklog_entry (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '工作日志ID',
    user_id       BIGINT       NOT NULL COMMENT '用户ID',
    work_date     DATE         NOT NULL COMMENT '归属日期',
    title         VARCHAR(200) NULL COMMENT '标题',
    done_text     MEDIUMTEXT   NULL COMMENT '今天做了什么',
    problem_text  MEDIUMTEXT   NULL COMMENT '遇到的问题与解决',
    summary_text  MEDIUMTEXT   NULL COMMENT '总结',
    plan_text     MEDIUMTEXT   NULL COMMENT '明日计划',
    tags          JSON         NULL COMMENT '标签',
    created_time  DATETIME     NOT NULL COMMENT '创建时间',
    updated_time  DATETIME     NOT NULL COMMENT '更新时间',
    deleted_time  DATETIME     NULL COMMENT '软删除时间',
    UNIQUE KEY uk_user_date (user_id, work_date),
    KEY idx_user_updated (user_id, updated_time),
    KEY idx_user_deleted (user_id, deleted_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每日工作日志';
