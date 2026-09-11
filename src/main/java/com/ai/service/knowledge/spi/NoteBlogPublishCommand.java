package com.ai.service.knowledge.spi;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 精读发布博客的命令。字段均由 knowledge 侧根据精读内容组装，blog 侧只负责落库。
 */
@Data
@Builder
public class NoteBlogPublishCommand {

    private String title;

    private String contentMd;

    private String summary;

    private Long categoryId;

    private List<Long> tagIds;

    /** 0 草稿 / 1 发布。 */
    private Integer status;

    private String extendInfo;

    private Long userId;
}
