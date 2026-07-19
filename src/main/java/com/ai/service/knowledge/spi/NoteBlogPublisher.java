package com.ai.service.knowledge.spi;

import com.ai.model.vo.blog.BlogPostVO;

/**
 * 精读发布到博客的 SPI。由 knowledge 模块定义，blog 模块提供真实实现，
 * blog 关闭时由 {@link NoOpNoteBlogPublisher} 兜底，避免 knowledge 强依赖 blog 的 Service Bean。
 */
public interface NoteBlogPublisher {

    /** 博客能力是否可用（blog 模块是否启用）。 */
    boolean isAvailable();

    /** 新建一篇博客文章并返回其 VO。 */
    BlogPostVO publish(NoteBlogPublishCommand command);

    /** 同步更新已关联的博客文章并返回最新 VO。 */
    BlogPostVO sync(NoteBlogSyncCommand command);

    /** 详情展示用；不可用或不存在时返回 {@code null}。 */
    BlogPostVO findBlogPost(Long blogPostId);
}
