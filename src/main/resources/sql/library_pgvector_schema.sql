-- 档案库语义搜索向量表（与知识库共用 knowledge_ai PostgreSQL 库）
-- 维度 1536 与 knowledge.vector 默认 embedding 模型（text-embedding-v2）一致；
-- 若更换 embedding 模型，需同步调整维度并重建该表。
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS library_file_embedding (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    file_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    embedding vector(1536) NOT NULL,
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_time TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_lfile_embed_file ON library_file_embedding (file_id);
CREATE INDEX IF NOT EXISTS idx_lfile_embed_user ON library_file_embedding (user_id);
