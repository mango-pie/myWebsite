package com.ai.service.knowledge;

import com.ai.model.entity.knowledge.KnowledgeNote;

public interface KnowledgeDistillationService {

    KnowledgeNote distillToMarkdown(Long sourceDocumentId, Long userId);

    KnowledgeNote distillToMarkdown(Long sourceDocumentId, Long userId, String tags);

    KnowledgeNote redistill(Long noteId, Long userId);

    KnowledgeNote retryDistillation(Long sourceDocumentId, Long userId);
}
