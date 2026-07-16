package com.ai.service.knowledge;

import com.ai.model.dto.knowledge.KnowledgeNoteIndexRequest;
import com.ai.model.vo.knowledge.KnowledgeDocumentVO;

public interface KnowledgeNoteIndexService {

    KnowledgeDocumentVO indexToKnowledgeBase(Long noteId, KnowledgeNoteIndexRequest request, Long userId);

    KnowledgeDocumentVO reindex(Long noteId, Long userId);
}
