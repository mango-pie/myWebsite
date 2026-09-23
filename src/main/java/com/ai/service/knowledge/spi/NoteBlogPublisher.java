package com.ai.service.knowledge.spi;

import com.ai.model.vo.blog.BlogPostVO;

/**
 * 精读发布到博客的 SPI。由 knowledge 模块定义，blog 模块提供真实实现，
 * blog 关闭时由 {@link NoOpNoteBlogPublisher} 兜底，避免 knowledge 强依赖 blog 的 Service Bean。
 *
 * <p>契约约定：允许的依赖只有 {@code model.vo.blog} 下的只读数据类
 * （BlogPostVO 等仅承载字段，无 Service/Mapper 引用）；实现在 blog 侧组装请求，
 * knowledge 侧不得 import 任何 blog 的 Service。VO 直接作为返回值是为保持
 * 前端 JSON 结构不变；若未来前端配合改造，可换为 knowledge 自有的中立 DTO。
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

    /**
     * 软删关联博客（与博客管理删除同语义）。
     * 文章不存在或已删除时应幂等成功，不向外抛「不存在」。
     */
    void softDelete(Long blogPostId, Long userId);
}
