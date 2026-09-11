package com.ai.service.knowledge;

import com.ai.config.ConditionalOnModule;
import com.ai.service.blog.spi.KnowledgeNoteUnlinker;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * knowledge 模块提供的 {@link KnowledgeNoteUnlinker} 真实实现，仅在 knowledge 启用时注册。
 */
@ConditionalOnModule("knowledge")
@Component
public class KnowledgeNoteUnlinkerImpl implements KnowledgeNoteUnlinker {

    @Resource
    private KnowledgeNoteService knowledgeNoteService;

    @Override
    public void clearBlogLinkByPostId(Long blogPostId, Long userId) {
        knowledgeNoteService.clearBlogLinkByPostId(blogPostId, userId);
    }
}
