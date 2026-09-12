-- AI 精读工作台二期: 复习状态 / 归档 / 对齐度 / 任务重试次数 / 用量 job 粒度
-- Idempotent: guarded by information_schema.

DELIMITER $$

DROP PROCEDURE IF EXISTS _reading_v2_upgrade $$
CREATE PROCEDURE _reading_v2_upgrade()
BEGIN
    DECLARE db VARCHAR(128);
    SET db = DATABASE();

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = db AND table_name = 'knowledge_note'
                     AND column_name = 'review_status') THEN
        ALTER TABLE knowledge_note ADD COLUMN review_status VARCHAR(32) NOT NULL DEFAULT 'NEW'
            COMMENT '复习状态 NEW/REVIEWING/MASTERED';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = db AND table_name = 'knowledge_note'
                     AND column_name = 'is_archived') THEN
        ALTER TABLE knowledge_note ADD COLUMN is_archived TINYINT NOT NULL DEFAULT 0
            COMMENT '0 正常 / 1 已归档(列表默认隐藏)';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = db AND table_name = 'knowledge_note'
                     AND column_name = 'alignment_score') THEN
        ALTER TABLE knowledge_note ADD COLUMN alignment_score DECIMAL(3,2) DEFAULT NULL
            COMMENT '与学习目标对齐度 0.00~1.00';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = db AND table_name = 'knowledge_note'
                     AND column_name = 'alignment_note') THEN
        ALTER TABLE knowledge_note ADD COLUMN alignment_note VARCHAR(500) DEFAULT NULL
            COMMENT '对齐度自检说明';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = db AND table_name = 'knowledge_reading_job'
                     AND column_name = 'retry_count') THEN
        ALTER TABLE knowledge_reading_job ADD COLUMN retry_count INT NOT NULL DEFAULT 0
            COMMENT '已重试次数，上限见服务常量';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = db AND table_name = 'ai_usage_log'
                     AND column_name = 'job_id') THEN
        ALTER TABLE ai_usage_log ADD COLUMN job_id BIGINT DEFAULT NULL
            COMMENT '精读任务 ID，用量看板按 job 聚合';
    END IF;
END $$

DELIMITER ;

CALL _reading_v2_upgrade();
DROP PROCEDURE IF EXISTS _reading_v2_upgrade;
