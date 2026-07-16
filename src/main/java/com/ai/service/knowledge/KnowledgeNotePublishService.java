package com.ai.service.knowledge;

import com.ai.model.dto.knowledge.KnowledgeNotePublishRequest;
import com.ai.model.vo.blog.BlogPostVO;

public interface KnowledgeNotePublishService {

    BlogPostVO publishToBlog(Long noteId, KnowledgeNotePublishRequest request, Long userId);

    BlogPostVO syncToBlog(Long noteId, Long userId);
}
