package com.ai.service.study.spi;

import java.util.List;

/**
 * 读取博客草稿的 SPI。由 study 模块定义，blog 模块提供真实实现，
 * blog 关闭时由 {@link NoOpBlogDraftReader} 兜底，避免 study 强依赖 blog 的 Mapper。
 */
public interface BlogDraftReader {

    /** blog 能力是否可用。 */
    boolean isAvailable();

    /** 列出指定用户的博客草稿（status = 0）。 */
    List<BlogDraftItem> listDraftsByUser(Long userId);
}
