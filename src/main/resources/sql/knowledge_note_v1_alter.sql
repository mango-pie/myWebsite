-- AI 精读工作台 V1：已有库增量迁移

ALTER TABLE knowledge_note
    ADD COLUMN knowledge_document_id BIGINT DEFAULT NULL COMMENT '精读稿入库后关联的知识库文档' AFTER blog_post_id,
    ADD COLUMN publish_status VARCHAR(32) NOT NULL DEFAULT 'NOT_PUBLISHED' COMMENT 'NOT_PUBLISHED/DRAFT_CREATED/PUBLISHED/SYNC_REQUIRED/SYNC_FAILED' AFTER status,
    ADD COLUMN index_status VARCHAR(32) NOT NULL DEFAULT 'NOT_INDEXED' COMMENT 'NOT_INDEXED/INDEXED/REINDEX_REQUIRED/INDEX_FAILED' AFTER publish_status,
    ADD COLUMN last_distilled_at DATETIME DEFAULT NULL COMMENT '最近 AI 蒸馏时间' AFTER view_count,
    ADD COLUMN last_edited_at DATETIME DEFAULT NULL COMMENT '最近人工编辑时间' AFTER last_distilled_at,
    ADD COLUMN last_published_at DATETIME DEFAULT NULL COMMENT '最近同步博客时间' AFTER last_edited_at,
    ADD COLUMN last_indexed_at DATETIME DEFAULT NULL COMMENT '最近向量入库时间' AFTER last_published_at,
    ADD KEY idx_knote_document (knowledge_document_id),
    ADD KEY idx_knote_publish_status (publish_status),
    ADD KEY idx_knote_index_status (index_status);
