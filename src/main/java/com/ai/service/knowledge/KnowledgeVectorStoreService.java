package com.ai.service.knowledge;

import com.ai.model.entity.knowledge.KnowledgeDocument;
import com.ai.model.vo.knowledge.KnowledgeChunkVO;
import com.ai.model.vo.knowledge.KnowledgeTextChunk;

import java.util.List;
import java.util.Map;

public interface KnowledgeVectorStoreService {

    void replaceChunks(KnowledgeDocument document, List<KnowledgeTextChunk> chunks, List<float[]> embeddings);

    List<KnowledgeChunkVO> listByDocument(Long documentId, int pageNum, int pageSize);

    List<KnowledgeChunkVO> search(Long knowledgeBaseId, Long sourceDocumentId, float[] queryEmbedding, int topK);

    void deleteByDocumentId(Long documentId);

    /** 差量返工：现有 chunk_index → 内容（用于 md5 对比，只重 embedding 变化的 chunk） */
    Map<Integer, String> chunkContentsByIndex(Long documentId);

    /** 差量返工：按 chunk_index 覆盖写入（先删后插，事务内） */
    void upsertChunks(KnowledgeDocument document, List<Integer> indexes,
                      List<KnowledgeTextChunk> chunks, List<float[]> embeddings);

    /** 差量返工：删除指定 chunk_index */
    void deleteChunkIndexes(Long documentId, List<Integer> indexes);

    /** 健康探测：执行 SELECT 1，不暴露连接串。 */
    boolean ping();
}
