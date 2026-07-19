package com.ai.service.knowledge.spi;

import lombok.Builder;
import lombok.Data;

/**
 * 精读同步到已关联博客的命令。
 */
@Data
@Builder
public class NoteBlogSyncCommand {

    private Long blogPostId;

    /** 精读标题，可能为空，blog 侧回退到原文章标题。 */
    private String fallbackTitle;

    private String contentMd;

    private String summary;

    private Long userId;
}
