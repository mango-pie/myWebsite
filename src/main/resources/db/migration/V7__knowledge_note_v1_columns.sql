-- AI reading workbench V1: add columns/indexes to knowledge_note.
-- Idempotent: guarded by information_schema so it is safe on databases that
-- already have these columns (e.g. installs created fresh by V6) and on older
-- knowledge databases that pre-date V1.

DELIMITER $$

DROP PROCEDURE IF EXISTS _knote_v1_upgrade $$
CREATE PROCEDURE _knote_v1_upgrade()
BEGIN
    DECLARE db VARCHAR(128);
    SET db = DATABASE();

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = db AND table_name = 'knowledge_note'
                     AND column_name = 'knowledge_document_id') THEN
        ALTER TABLE knowledge_note ADD COLUMN knowledge_document_id BIGINT DEFAULT NULL
            COMMENT 'related knowledge document id after ingestion';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = db AND table_name = 'knowledge_note'
                     AND column_name = 'publish_status') THEN
        ALTER TABLE knowledge_note ADD COLUMN publish_status VARCHAR(32) NOT NULL DEFAULT 'NOT_PUBLISHED'
            COMMENT 'NOT_PUBLISHED/DRAFT_CREATED/PUBLISHED/SYNC_REQUIRED/SYNC_FAILED';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = db AND table_name = 'knowledge_note'
                     AND column_name = 'index_status') THEN
        ALTER TABLE knowledge_note ADD COLUMN index_status VARCHAR(32) NOT NULL DEFAULT 'NOT_INDEXED'
            COMMENT 'NOT_INDEXED/INDEXED/REINDEX_REQUIRED/INDEX_FAILED';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = db AND table_name = 'knowledge_note'
                     AND column_name = 'last_distilled_at') THEN
        ALTER TABLE knowledge_note ADD COLUMN last_distilled_at DATETIME DEFAULT NULL
            COMMENT 'last AI distill time';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = db AND table_name = 'knowledge_note'
                     AND column_name = 'last_edited_at') THEN
        ALTER TABLE knowledge_note ADD COLUMN last_edited_at DATETIME DEFAULT NULL
            COMMENT 'last manual edit time';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = db AND table_name = 'knowledge_note'
                     AND column_name = 'last_published_at') THEN
        ALTER TABLE knowledge_note ADD COLUMN last_published_at DATETIME DEFAULT NULL
            COMMENT 'last blog sync time';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = db AND table_name = 'knowledge_note'
                     AND column_name = 'last_indexed_at') THEN
        ALTER TABLE knowledge_note ADD COLUMN last_indexed_at DATETIME DEFAULT NULL
            COMMENT 'last vector index time';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = db AND table_name = 'knowledge_note'
                     AND index_name = 'idx_knote_document') THEN
        ALTER TABLE knowledge_note ADD KEY idx_knote_document (knowledge_document_id);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = db AND table_name = 'knowledge_note'
                     AND index_name = 'idx_knote_publish_status') THEN
        ALTER TABLE knowledge_note ADD KEY idx_knote_publish_status (publish_status);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = db AND table_name = 'knowledge_note'
                     AND index_name = 'idx_knote_index_status') THEN
        ALTER TABLE knowledge_note ADD KEY idx_knote_index_status (index_status);
    END IF;
END $$

DELIMITER ;

CALL _knote_v1_upgrade();
DROP PROCEDURE IF EXISTS _knote_v1_upgrade;
