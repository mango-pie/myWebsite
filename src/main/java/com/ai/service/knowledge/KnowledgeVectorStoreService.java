package com.ai.service.knowledge;

import com.ai.model.entity.knowledge.KnowledgeDocument;
import com.ai.model.vo.knowledge.KnowledgeChunkVO;
import com.ai.model.vo.knowledge.KnowledgeTextChunk;

import java.util.List;

public interface KnowledgeVectorStoreService {

    void replaceChunks(KnowledgeDocument document, List<KnowledgeTextChunk> chunks, List<float[]> embeddings);

    List<KnowledgeChunkVO> listByDocument(Long documentId, int pageNum, int pageSize);

    List<KnowledgeChunkVO> search(Long knowledgeBaseId, Long sourceDocumentId, float[] queryEmbedding, int topK);

    void deleteByDocumentId(Long documentId);

    /** 健康探测：执行 SELECT 1，不暴露连接串。 */
    boolean ping();
}
