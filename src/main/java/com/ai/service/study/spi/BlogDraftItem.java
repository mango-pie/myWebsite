package com.ai.service.study.spi;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * study 侧需要的博客草稿轻量视图，避免直接依赖 blog 的实体/Mapper。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlogDraftItem {

    private Long id;

    private String title;
}
