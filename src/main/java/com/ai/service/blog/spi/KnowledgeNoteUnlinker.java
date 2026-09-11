package com.ai.service.blog.spi;

/**
 * 博客删除后回写精读关联的 SPI。由 blog 模块定义，knowledge 模块提供真实实现。
 * knowledge 关闭时由 {@link NoOpKnowledgeNoteUnlinker} 兜底，避免 blog 强依赖 knowledge 的 Service。
 */
public interface KnowledgeNoteUnlinker {

    /**
     * 博客软删后回写：清空关联精读的 blogPostId，publishStatus 置为未发布。
     * 无关联 note 或 knowledge 未启用时幂等 no-op。
     */
    void clearBlogLinkByPostId(Long blogPostId, Long userId);
}
