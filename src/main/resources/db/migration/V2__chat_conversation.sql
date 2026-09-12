-- 角色聊天模块 DDL（与代码生成 app/chat_history 分离）

CREATE TABLE IF NOT EXISTS chat_conversation (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '会话ID',
    user_id             BIGINT       NOT NULL COMMENT '用户ID',
    config_id           VARCHAR(64)  NOT NULL COMMENT 'AstrBot 预设/角色ID',
    config_name         VARCHAR(128) NULL COMMENT '角色展示名冗余',
    title               VARCHAR(256) NULL COMMENT '会话标题/摘要',
    astrbot_session_id  VARCHAR(128) NOT NULL COMMENT 'AstrBot session_id，稳定唯一',
    is_default          TINYINT      NOT NULL DEFAULT 0 COMMENT '1=该角色下的默认会话',
    last_message_at     DATETIME     NULL COMMENT '最后一条消息时间',
    create_time         DATETIME     NOT NULL COMMENT '创建时间',
    update_time         DATETIME     NOT NULL COMMENT '更新时间',
    is_delete           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    UNIQUE KEY uk_astrbot_session (astrbot_session_id),
    KEY idx_user_config (user_id, config_id, is_delete, last_message_at),
    KEY idx_user_default (user_id, config_id, is_default, is_delete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色聊天会话';

CREATE TABLE IF NOT EXISTS chat_message (
    id                BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花ID',
    conversation_id   BIGINT       NOT NULL COMMENT '会话ID',
    user_id           BIGINT       NOT NULL COMMENT '用户ID',
    message_type      VARCHAR(16)  NOT NULL COMMENT 'user/ai/error',
    content           TEXT         NOT NULL COMMENT '消息正文',
    source            VARCHAR(16)  NOT NULL DEFAULT 'normal' COMMENT 'normal/proactive',
    parent_id         BIGINT       NULL COMMENT '父消息ID',
    create_time       DATETIME     NOT NULL COMMENT '创建时间',
    update_time       DATETIME     NOT NULL COMMENT '更新时间',
    is_delete         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    KEY idx_conv_time (conversation_id, is_delete, create_time),
    KEY idx_user_conv (user_id, conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色聊天消息';

-- ---------------------------------------------------------------------------
-- 可选：将旧角色聊天数据从 chat_history 迁入（需人工确认后执行，默认不跑）
-- 仅迁移 app 表中 codeGenType 为空的「误建聊天 App」
-- ---------------------------------------------------------------------------
-- INSERT INTO chat_conversation (user_id, config_id, config_name, title, astrbot_session_id, is_default, create_time, update_time, is_delete)
-- SELECT DISTINCT
--     a.userId,
--     'legacy',
--     a.appName,
--     CONCAT('迁移自应用 ', a.appName),
--     CONCAT('chat_user_', a.userId, '_cfg_legacy_conv_m', a.id),
--     1,
--     a.createTime,
--     a.updateTime,
--     0
-- FROM app a
-- WHERE a.isDelete = 0
--   AND (a.codeGenType IS NULL OR a.codeGenType = '')
--   AND NOT EXISTS (
--       SELECT 1 FROM chat_conversation c
--       WHERE c.user_id = a.userId AND c.config_id = 'legacy' AND c.title LIKE CONCAT('%', a.appName, '%')
--   );
