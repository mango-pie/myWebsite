-- Knowledge AI integration schema for Ai-Backend (MySQL).

CREATE TABLE IF NOT EXISTS knowledge_base (
    id BIGINT NOT NULL PRIMARY KEY,
    name VARCHAR(128) NOT NULL COMMENT '知识库名称',
    description VARCHAR(512) DEFAULT NULL COMMENT '知识库描述',
    user_id BIGINT NOT NULL COMMENT '创建用户',
    visibility VARCHAR(32) NOT NULL DEFAULT 'PRIVATE' COMMENT 'PRIVATE/PUBLIC',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
    document_count INT NOT NULL DEFAULT 0 COMMENT '文档数量',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_delete TINYINT NOT NULL DEFAULT 0,
    KEY idx_kb_user_deleted (user_id, is_delete),
    KEY idx_kb_user_name (user_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库';

CREATE TABLE IF NOT EXISTS knowledge_document (
    id BIGINT NOT NULL PRIMARY KEY,
    knowledge_base_id BIGINT NOT NULL COMMENT '知识库ID',
    user_id BIGINT NOT NULL COMMENT '上传用户',
    source_document_id BIGINT DEFAULT NULL COMMENT '源文档ID',
    file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_type VARCHAR(32) NOT NULL COMMENT '文件类型',
    file_size BIGINT NOT NULL COMMENT '文件大小',
    bucket_name VARCHAR(128) NOT NULL COMMENT 'MinIO bucket',
    object_key VARCHAR(512) NOT NULL COMMENT 'MinIO object key',
    parse_status VARCHAR(32) NOT NULL DEFAULT 'UPLOADED' COMMENT 'UPLOADED/PARSING/PARSED/FAILED',
    chunk_count INT NOT NULL DEFAULT 0,
    error_message TEXT DEFAULT NULL,
    parsed_at DATETIME DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_delete TINYINT NOT NULL DEFAULT 0,
    KEY idx_kdoc_kb_deleted (knowledge_base_id, is_delete),
    KEY idx_kdoc_user_deleted (user_id, is_delete),
    KEY idx_kdoc_source (source_document_id),
    KEY idx_kdoc_parse_status (parse_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档';

CREATE TABLE IF NOT EXISTS source_document (
    id BIGINT NOT NULL PRIMARY KEY,
    title VARCHAR(255) DEFAULT NULL,
    user_id BIGINT NOT NULL,
    knowledge_base_id BIGINT DEFAULT NULL,
    knowledge_document_id BIGINT DEFAULT NULL,
    source_type VARCHAR(32) NOT NULL COMMENT 'URL/FILE/AGENT',
    source_url VARCHAR(2048) DEFAULT NULL,
    bucket_name VARCHAR(128) DEFAULT NULL,
    object_key VARCHAR(512) DEFAULT NULL,
    raw_text LONGTEXT DEFAULT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/SUCCESS/PARTIAL/FAILED',
    error_msg TEXT DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_delete TINYINT NOT NULL DEFAULT 0,
    KEY idx_source_user_deleted (user_id, is_delete),
    KEY idx_source_kb (knowledge_base_id),
    KEY idx_source_doc (knowledge_document_id),
    KEY idx_source_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一源文档';

CREATE TABLE IF NOT EXISTS knowledge_note (
    id BIGINT NOT NULL PRIMARY KEY,
    source_document_id BIGINT NOT NULL,
    blog_post_id BIGINT DEFAULT NULL COMMENT '关联博客文章，人工发布后写入',
    knowledge_document_id BIGINT DEFAULT NULL COMMENT '精读稿入库后关联的知识库文档',
    title VARCHAR(255) DEFAULT NULL,
    distilled_md LONGTEXT DEFAULT NULL,
    tags VARCHAR(255) DEFAULT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS/FAILED/DELETED',
    publish_status VARCHAR(32) NOT NULL DEFAULT 'NOT_PUBLISHED' COMMENT 'NOT_PUBLISHED/DRAFT_CREATED/PUBLISHED/SYNC_REQUIRED/SYNC_FAILED',
    index_status VARCHAR(32) NOT NULL DEFAULT 'NOT_INDEXED' COMMENT 'NOT_INDEXED/INDEXED/REINDEX_REQUIRED/INDEX_FAILED',
    error_msg TEXT DEFAULT NULL,
    view_count INT NOT NULL DEFAULT 0,
    last_distilled_at DATETIME DEFAULT NULL COMMENT '最近 AI 蒸馏时间',
    last_edited_at DATETIME DEFAULT NULL COMMENT '最近人工编辑时间',
    last_published_at DATETIME DEFAULT NULL COMMENT '最近同步博客时间',
    last_indexed_at DATETIME DEFAULT NULL COMMENT '最近向量入库时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_delete TINYINT NOT NULL DEFAULT 0,
    KEY idx_knote_source (source_document_id),
    KEY idx_knote_blog (blog_post_id),
    KEY idx_knote_document (knowledge_document_id),
    KEY idx_knote_status (status),
    KEY idx_knote_publish_status (publish_status),
    KEY idx_knote_index_status (index_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI精读笔记';

CREATE TABLE IF NOT EXISTS knowledge_processing_task (
    id BIGINT NOT NULL PRIMARY KEY,
    source_document_id BIGINT DEFAULT NULL,
    knowledge_document_id BIGINT DEFAULT NULL,
    task_type VARCHAR(32) NOT NULL COMMENT 'INGEST/DISTILL/RAG/RETRY',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    error_msg TEXT DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_delete TINYINT NOT NULL DEFAULT 0,
    KEY idx_ktask_source (source_document_id),
    KEY idx_ktask_doc (knowledge_document_id),
    KEY idx_ktask_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识处理任务';

CREATE TABLE IF NOT EXISTS knowledge_conversation (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    knowledge_base_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL DEFAULT '新会话',
    last_message VARCHAR(512) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_delete TINYINT NOT NULL DEFAULT 0,
    KEY idx_kconv_user_kb (user_id, knowledge_base_id, is_delete),
    KEY idx_kconv_update (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库问答会话';

CREATE TABLE IF NOT EXISTS knowledge_message (
    id BIGINT NOT NULL PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL COMMENT 'USER/ASSISTANT/SYSTEM',
    content LONGTEXT NOT NULL,
    model_name VARCHAR(128) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_delete TINYINT NOT NULL DEFAULT 0,
    KEY idx_kmsg_conv (conversation_id, is_delete),
    KEY idx_kmsg_user (user_id, is_delete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库问答消息';

CREATE TABLE IF NOT EXISTS knowledge_message_reference (
    id BIGINT NOT NULL PRIMARY KEY,
    message_id BIGINT NOT NULL,
    knowledge_document_id BIGINT DEFAULT NULL,
    source_document_id BIGINT DEFAULT NULL,
    chunk_id BIGINT DEFAULT NULL,
    chunk_index INT DEFAULT NULL,
    document_name VARCHAR(255) DEFAULT NULL,
    content TEXT DEFAULT NULL,
    similarity DECIMAL(10,6) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_delete TINYINT NOT NULL DEFAULT 0,
    KEY idx_kref_msg (message_id),
    KEY idx_kref_doc (knowledge_document_id),
    KEY idx_kref_source (source_document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库回答引用';
