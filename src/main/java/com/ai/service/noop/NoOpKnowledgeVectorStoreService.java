package com.ai.service.noop;

import com.ai.config.ConditionalOnModule;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.model.entity.knowledge.KnowledgeDocument;
import com.ai.model.vo.knowledge.KnowledgeChunkVO;
import com.ai.model.vo.knowledge.KnowledgeTextChunk;
import com.ai.service.knowledge.KnowledgeVectorStoreService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 本机不连 pgvector（知识库在其它服务器）时使用，避免再开一套 PostgreSQL 连接池。
 */
@Service
@ConditionalOnModule("knowledge")
@ConditionalOnProperty(name = "knowledge.vector.enabled", havingValue = "false")
public class NoOpKnowledgeVectorStoreService implements KnowledgeVectorStoreService {

    private static BusinessException disabled() {
        return new BusinessException(ErrorCode.OPERATION_ERROR,
                "本机未启用向量库（knowledge.vector.enabled=false），请到知识库所在服务器操作");
    }

    @Override
    public void replaceChunks(KnowledgeDocument document, List<KnowledgeTextChunk> chunks, List<float[]> embeddings) {
        throw disabled();
    }

    @Override
    public List<KnowledgeChunkVO> listByDocument(Long documentId, int pageNum, int pageSize) {
        return List.of();
    }

    @Override
    public List<KnowledgeChunkVO> search(Long knowledgeBaseId, Long sourceDocumentId, float[] queryEmbedding, int topK) {
        return List.of();
    }

    @Override
    public void deleteByDocumentId(Long documentId) {
    }

    @Override
    public Map<Integer, String> chunkContentsByIndex(Long documentId) {
        return Map.of();
    }

    @Override
    public void upsertChunks(KnowledgeDocument document, List<Integer> indexes,
                             List<KnowledgeTextChunk> chunks, List<float[]> embeddings) {
        throw disabled();
    }

    @Override
    public void deleteChunkIndexes(Long documentId, List<Integer> indexes) {
    }

    @Override
    public boolean ping() {
        return false;
    }
}
