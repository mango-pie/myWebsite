package com.ai.service.knowledge.impl;

import com.ai.config.ConditionalOnModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.ai.config.knowledge.KnowledgeVectorProperties;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.model.entity.knowledge.KnowledgeDocument;
import com.ai.model.vo.knowledge.KnowledgeChunkVO;
import com.ai.model.vo.knowledge.KnowledgeTextChunk;
import com.ai.service.knowledge.KnowledgeVectorStoreService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@ConditionalOnModule("knowledge")
@ConditionalOnProperty(name = "knowledge.vector.enabled", havingValue = "true", matchIfMissing = true)
@Service
public class KnowledgeVectorStoreServiceImpl implements KnowledgeVectorStoreService {

    private final HikariDataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public KnowledgeVectorStoreServiceImpl(KnowledgeVectorProperties properties) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.getUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10000);
        config.setConnectionTestQuery("SELECT 1");
        config.setInitializationFailTimeout(-1);
        this.dataSource = new HikariDataSource(config);
        this.jdbcTemplate = new JdbcTemplate(this.dataSource);
        this.transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(this.dataSource));
        log.info("Knowledge vector store initialized, url={}", properties.getUrl());
    }

    @PreDestroy
    public void destroy() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Override
    public void replaceChunks(KnowledgeDocument document, List<KnowledgeTextChunk> chunks, List<float[]> embeddings) {
        if (chunks.size() != embeddings.size()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Chunk 数量与向量数量不一致");
        }
        try {
            transactionTemplate.executeWithoutResult(status -> {
                deleteByDocumentIdInternal(document.getId());
                saveChunks(document, chunks, embeddings);
            });
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Replace knowledge chunks failed, documentId={}", document.getId(), e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "保存向量数据失败，请确认 PostgreSQL/pgvector 可用");
        }
    }

    @Override
    public List<KnowledgeChunkVO> listByDocument(Long documentId, int pageNum, int pageSize) {
        int size = Math.max(1, pageSize);
        int offset = (Math.max(1, pageNum) - 1) * size;
        try {
            return jdbcTemplate.query("""
                            SELECT id, knowledge_base_id, knowledge_document_id, source_document_id,
                                   chunk_index, heading, content, token_count, metadata::text, create_time
                            FROM knowledge_document_chunk
                            WHERE knowledge_document_id = ?
                            ORDER BY chunk_index ASC
                            LIMIT ? OFFSET ?
                            """,
                    this::mapChunk,
                    documentId,
                    size,
                    offset);
        } catch (DataAccessException e) {
            throw toVectorStoreException("查询切块失败", e);
        }
    }

    @Override
    public List<KnowledgeChunkVO> search(Long knowledgeBaseId, Long sourceDocumentId, float[] queryEmbedding, int topK) {
        String vector = toVectorLiteral(queryEmbedding);
        try {
            if (sourceDocumentId != null) {
                return jdbcTemplate.query("""
                                SELECT id, knowledge_base_id, knowledge_document_id, source_document_id,
                                       chunk_index, heading, content, token_count, metadata::text, create_time,
                                       1 - (embedding <=> CAST(? AS vector)) AS score
                                FROM knowledge_document_chunk
                                WHERE source_document_id = ?
                                ORDER BY embedding <=> CAST(? AS vector)
                                LIMIT ?
                                """,
                        this::mapSearchResult,
                        vector,
                        sourceDocumentId,
                        vector,
                        Math.max(1, topK));
            }
            return jdbcTemplate.query("""
                            SELECT id, knowledge_base_id, knowledge_document_id, source_document_id,
                                   chunk_index, heading, content, token_count, metadata::text, create_time,
                                   1 - (embedding <=> CAST(? AS vector)) AS score
                            FROM knowledge_document_chunk
                            WHERE knowledge_base_id = ?
                            ORDER BY embedding <=> CAST(? AS vector)
                            LIMIT ?
                            """,
                    this::mapSearchResult,
                    vector,
                    knowledgeBaseId,
                    vector,
                    Math.max(1, topK));
        } catch (DataAccessException e) {
            throw toVectorStoreException("向量检索失败", e);
        }
    }

    @Override
    public void deleteByDocumentId(Long documentId) {
        try {
            deleteByDocumentIdInternal(documentId);
        } catch (Exception e) {
            log.error("Delete knowledge chunks failed, documentId={}", documentId, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "删除向量数据失败");
        }
    }

    @Override
    public Map<Integer, String> chunkContentsByIndex(Long documentId) {
        try {
            List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT chunk_index, content FROM knowledge_document_chunk WHERE knowledge_document_id = ?",
                    documentId);
            java.util.Map<Integer, String> result = new java.util.HashMap<>();
            for (java.util.Map<String, Object> row : rows) {
                Object idx = row.get("chunk_index");
                Object content = row.get("content");
                if (idx != null && content != null) {
                    result.put(((Number) idx).intValue(), String.valueOf(content));
                }
            }
            return result;
        } catch (DataAccessException e) {
            throw toVectorStoreException("读取现有切块失败", e);
        }
    }

    @Override
    public void upsertChunks(KnowledgeDocument document, List<Integer> indexes,
                             List<KnowledgeTextChunk> chunks, List<float[]> embeddings) {
        if (chunks.size() != embeddings.size() || chunks.size() != indexes.size()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Chunk / index / embedding 数量不一致");
        }
        if (chunks.isEmpty()) {
            return;
        }
        try {
            transactionTemplate.executeWithoutResult(status -> {
                for (int i = 0; i < chunks.size(); i++) {
                    jdbcTemplate.update(
                            "DELETE FROM knowledge_document_chunk WHERE knowledge_document_id = ? AND chunk_index = ?",
                            document.getId(), indexes.get(i));
                }
                saveChunksByIndexes(document, indexes, chunks, embeddings);
            });
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Upsert knowledge chunks failed, documentId={}", document.getId(), e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "差量保存向量数据失败，请确认 PostgreSQL/pgvector 可用");
        }
    }

    @Override
    public void deleteChunkIndexes(Long documentId, List<Integer> indexes) {
        if (indexes == null || indexes.isEmpty()) {
            return;
        }
        try {
            String in = String.join(",", java.util.Collections.nCopies(indexes.size(), "?"));
            jdbcTemplate.update(
                    "DELETE FROM knowledge_document_chunk WHERE knowledge_document_id = ? AND chunk_index IN (" + in + ")",
                    argumentList(documentId, indexes).toArray());
        } catch (Exception e) {
            log.error("Delete chunk indexes failed, documentId={}", documentId, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "删除过期切块失败");
        }
    }

    private List<Object> argumentList(Long documentId, List<Integer> indexes) {
        List<Object> args = new ArrayList<>();
        args.add(documentId);
        args.addAll(indexes);
        return args;
    }

    private void saveChunksByIndexes(KnowledgeDocument document, List<Integer> indexes,
                                     List<KnowledgeTextChunk> chunks, List<float[]> embeddings) {
        String sql = """
                INSERT INTO knowledge_document_chunk
                (knowledge_base_id, knowledge_document_id, source_document_id, chunk_index, heading,
                 content, token_count, embedding, metadata, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS vector), CAST(? AS jsonb), now(), now())
                """;
        List<Object[]> args = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            KnowledgeTextChunk chunk = chunks.get(i);
            args.add(new Object[]{
                    document.getKnowledgeBaseId(),
                    document.getId(),
                    document.getSourceDocumentId(),
                    indexes.get(i),
                    chunk.heading(),
                    chunk.content(),
                    chunk.tokenCount(),
                    toVectorLiteral(embeddings.get(i)),
                    chunk.metadata()
            });
        }
        jdbcTemplate.batchUpdate(sql, args);
    }

    private void saveChunks(KnowledgeDocument document, List<KnowledgeTextChunk> chunks, List<float[]> embeddings) {
        String sql = """
                INSERT INTO knowledge_document_chunk
                (knowledge_base_id, knowledge_document_id, source_document_id, chunk_index, heading,
                 content, token_count, embedding, metadata, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS vector), CAST(? AS jsonb), now(), now())
                """;
        List<Object[]> args = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            KnowledgeTextChunk chunk = chunks.get(i);
            args.add(new Object[]{
                    document.getKnowledgeBaseId(),
                    document.getId(),
                    document.getSourceDocumentId(),
                    chunk.chunkIndex(),
                    chunk.heading(),
                    chunk.content(),
                    chunk.tokenCount(),
                    toVectorLiteral(embeddings.get(i)),
                    chunk.metadata()
            });
        }
        jdbcTemplate.batchUpdate(sql, args);
    }

    private void deleteByDocumentIdInternal(Long documentId) {
        jdbcTemplate.update("DELETE FROM knowledge_document_chunk WHERE knowledge_document_id = ?", documentId);
    }

    private KnowledgeChunkVO mapChunk(ResultSet rs, int rowNum) throws SQLException {
        Timestamp createTime = rs.getTimestamp("create_time");
        return new KnowledgeChunkVO(
                rs.getLong("id"),
                rs.getLong("knowledge_base_id"),
                rs.getLong("knowledge_document_id"),
                rs.getObject("source_document_id") == null ? null : rs.getLong("source_document_id"),
                rs.getInt("chunk_index"),
                rs.getString("heading"),
                rs.getString("content"),
                rs.getInt("token_count"),
                rs.getString("metadata"),
                createTime == null ? null : createTime.toLocalDateTime(),
                null
        );
    }

    private KnowledgeChunkVO mapSearchResult(ResultSet rs, int rowNum) throws SQLException {
        KnowledgeChunkVO chunk = mapChunk(rs, rowNum);
        chunk.setScore(rs.getDouble("score"));
        return chunk;
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

    @Override
    public boolean ping() {
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return one != null && one == 1;
        } catch (Exception e) {
            log.warn("Knowledge vector store ping failed: {}", e.getMessage());
            return false;
        }
    }

    private BusinessException toVectorStoreException(String action, DataAccessException e) {
        String detail = rootCauseMessage(e);
        log.error("{}: {}", action, detail, e);
        if (detail != null && detail.contains("knowledge_document_chunk") && detail.contains("does not exist")) {
            return new BusinessException(ErrorCode.OPERATION_ERROR,
                    action + "：PostgreSQL 中缺少 knowledge_document_chunk 表，请执行 knowledge_pgvector_schema.sql");
        }
        return new BusinessException(ErrorCode.OPERATION_ERROR,
                action + "，请确认 PostgreSQL/pgvector 可用：" + detail);
    }

    private String rootCauseMessage(DataAccessException e) {
        return e.getMostSpecificCause() == null ? e.getMessage() : e.getMostSpecificCause().getMessage();
    }
}
