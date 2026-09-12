-- 日记模块 DDL（私有、每用户每天一篇）

CREATE TABLE IF NOT EXISTS diary_entry (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日记ID',
    user_id      BIGINT       NOT NULL COMMENT '用户ID',
    diary_date   DATE         NOT NULL COMMENT '日记归属日期',
    title        VARCHAR(200) NULL COMMENT '标题（可选）',
    content      LONGTEXT     NOT NULL COMMENT '正文 Markdown',
    mood         VARCHAR(32)  NULL COMMENT '心情',
    weather      VARCHAR(32)  NULL COMMENT '天气',
    tags         JSON         NULL COMMENT '个人标签',
    status       TINYINT      NOT NULL DEFAULT 0 COMMENT '0草稿 1完成',
    word_count   INT          NOT NULL DEFAULT 0 COMMENT '字数',
    cover_url    VARCHAR(500) NULL COMMENT '头图',
    extend_info  JSON         NULL COMMENT '扩展信息（AI等）',
    created_time DATETIME     NOT NULL COMMENT '创建时间',
    updated_time DATETIME     NOT NULL COMMENT '更新时间',
    deleted_time DATETIME     NULL COMMENT '软删除时间',
    UNIQUE KEY uk_user_date (user_id, diary_date),
    KEY idx_user_updated (user_id, updated_time),
    KEY idx_user_deleted (user_id, deleted_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户日记表';
