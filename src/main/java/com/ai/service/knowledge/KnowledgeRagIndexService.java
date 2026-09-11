package com.ai.service.knowledge;

import com.ai.model.vo.knowledge.KnowledgeDocumentVO;

public interface KnowledgeRagIndexService {

    KnowledgeDocumentVO indexSourceDocument(Long sourceDocumentId, Long userId);

    KnowledgeDocumentVO reindexSourceDocument(Long sourceDocumentId, Long userId);
}
