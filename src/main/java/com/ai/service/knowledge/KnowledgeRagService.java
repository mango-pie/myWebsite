package com.ai.service.knowledge;

import com.ai.model.vo.knowledge.KnowledgeChunkVO;

import java.util.List;

public interface KnowledgeRagService {

    List<KnowledgeChunkVO> searchChunks(Long knowledgeBaseId, Long sourceDocumentId, String question, Integer topK);
}
