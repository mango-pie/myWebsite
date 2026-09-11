package com.ai.service.blog.spi;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * knowledge 模块关闭时的兜底实现；与 KnowledgeNoteUnlinkerImpl 互斥。
 */
@Component
@ConditionalOnProperty(name = "app.modules.knowledge", havingValue = "false")
public class NoOpKnowledgeNoteUnlinker implements KnowledgeNoteUnlinker {

    @Override
    public void clearBlogLinkByPostId(Long blogPostId, Long userId) {
        // knowledge 未启用：无精读关联可断开
    }
}
