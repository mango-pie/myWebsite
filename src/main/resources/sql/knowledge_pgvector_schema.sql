CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS knowledge_document_chunk (
    id BIGSERIAL PRIMARY KEY,
    knowledge_base_id BIGINT NOT NULL,
    knowledge_document_id BIGINT NOT NULL,
    source_document_id BIGINT,
    note_id BIGINT,
    chunk_index INT NOT NULL,
    heading VARCHAR(255),
    content TEXT NOT NULL,
    token_count INT NOT NULL DEFAULT 0,
    embedding vector(1536) NOT NULL,
    metadata JSONB DEFAULT '{}'::jsonb,
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_time TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_kchunk_doc ON knowledge_document_chunk (knowledge_document_id);
CREATE INDEX IF NOT EXISTS idx_kchunk_source ON knowledge_document_chunk (source_document_id);
CREATE INDEX IF NOT EXISTS idx_kchunk_kb ON knowledge_document_chunk (knowledge_base_id);
CREATE INDEX IF NOT EXISTS idx_kchunk_embedding_hnsw
    ON knowledge_document_chunk USING hnsw (embedding vector_cosine_ops);
