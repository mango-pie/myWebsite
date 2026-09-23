package com.ai.service.library.impl;

import com.ai.config.ConditionalOnModule;
import com.ai.config.knowledge.KnowledgeVectorProperties;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 档案库语义搜索向量存取（pgvector）。
 * 表结构见 sql/library_pgvector_schema.sql。
 *
 * <p>复用 knowledge.vector.datasource 连接配置是有意的共享基础设施设计：
 * 资料库与知识库的向量表同库不同表（library_file_embedding / knowledge_*），
 * 共用同一个 pgvector 实例，因此配置类归知识库模块统一持有。
 * 这是纯配置级依赖，本类不含对 knowledge 任何 Service 的引用。
 */
@Slf4j
@ConditionalOnModule("library")
@Service
public class LibraryVectorService {

    private final HikariDataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public LibraryVectorService(KnowledgeVectorProperties properties) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.getUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(3);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10000);
        config.setConnectionTestQuery("SELECT 1");
        config.setInitializationFailTimeout(-1);
        this.dataSource = new HikariDataSource(config);
        this.jdbcTemplate = new JdbcTemplate(this.dataSource);
        ensureSchema();
    }

    @PreDestroy
    public void destroy() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    /** 启动时尽力建表；失败只告警，不阻断启动（也可手动执行 library_pgvector_schema.sql） */
    private void ensureSchema() {
        try {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS library_file_embedding (
                        id BIGSERIAL PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        file_id BIGINT NOT NULL,
                        content TEXT NOT NULL,
                        embedding vector(1536) NOT NULL,
                        create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                        update_time TIMESTAMPTZ NOT NULL DEFAULT NOW()
                    )""");
            jdbcTemplate.execute(
                    "CREATE UNIQUE INDEX IF NOT EXISTS idx_lfile_embed_file ON library_file_embedding (file_id)");
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_lfile_embed_user ON library_file_embedding (user_id)");
        } catch (Exception e) {
            log.warn("Library embedding schema ensure failed (semantic search may be unavailable): {}",
                    e.getMessage());
        }
    }

    public boolean ping() {
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return one != null && one == 1;
        } catch (Exception e) {
            return false;
        }
    }

    /** 覆盖式写入某个档案文件的向量 */
    public void upsert(Long userId, Long fileId, String content, float[] embedding) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO library_file_embedding (user_id, file_id, content, embedding, update_time)
                    VALUES (?, ?, ?, CAST(? AS vector), now())
                    ON CONFLICT (file_id) DO UPDATE
                    SET user_id = EXCLUDED.user_id,
                        content = EXCLUDED.content,
                        embedding = EXCLUDED.embedding,
                        update_time = now()
                    """,
                    userId, fileId, content, toVectorLiteral(embedding));
        } catch (DataAccessException e) {
            log.warn("Library embedding upsert failed, fileId={}: {}", fileId, rootMessage(e));
        }
    }

    public void deleteByFileId(Long fileId) {
        try {
            jdbcTemplate.update("DELETE FROM library_file_embedding WHERE file_id = ?", fileId);
        } catch (DataAccessException e) {
            log.warn("Library embedding delete failed, fileId={}: {}", fileId, rootMessage(e));
        }
    }

    /** 已有向量的 file_id 集合（用于回填判断） */
    public List<Long> existingFileIds(List<Long> fileIds) {
        if (fileIds.isEmpty()) {
            return List.of();
        }
        try {
            return jdbcTemplate.query(
                    "SELECT file_id FROM library_file_embedding WHERE file_id IN ("
                            + placeholders(fileIds.size()) + ")",
                    (rs, i) -> rs.getLong("file_id"),
                    fileIds.toArray());
        } catch (DataAccessException e) {
            log.warn("Library embedding existing query failed: {}", rootMessage(e));
            return List.of();
        }
    }

    public record VectorHit(Long fileId, double score) {
    }

    public List<VectorHit> search(Long userId, float[] queryEmbedding, int topK) {
        String vector = toVectorLiteral(queryEmbedding);
        try {
            return jdbcTemplate.query("""
                            SELECT file_id, 1 - (embedding <=> CAST(? AS vector)) AS score
                            FROM library_file_embedding
                            WHERE user_id = ?
                            ORDER BY embedding <=> CAST(? AS vector)
                            LIMIT ?
                            """,
                    (rs, i) -> new VectorHit(rs.getLong("file_id"), rs.getDouble("score")),
                    vector, userId, vector, Math.max(1, topK));
        } catch (DataAccessException e) {
            throw new IllegalStateException("档案库向量检索失败：" + rootMessage(e), e);
        }
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(embedding[i]);
        }
        return builder.append(']').toString();
    }

    private String placeholders(int n) {
        List<String> marks = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            marks.add("?");
        }
        return String.join(",", marks);
    }

    private String rootMessage(DataAccessException e) {
        return e.getMostSpecificCause() == null ? e.getMessage() : e.getMostSpecificCause().getMessage();
    }
}
