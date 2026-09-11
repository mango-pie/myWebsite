package com.ai.service.blog;

import com.ai.config.ConditionalOnModule;
import com.ai.mapper.blog.BlogPostMapper;
import com.ai.model.entity.BlogPost;
import com.ai.service.study.spi.BlogDraftItem;
import com.ai.service.study.spi.BlogDraftReader;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * blog 模块提供的 {@link BlogDraftReader} 真实实现，仅在 blog 模块启用时注册。
 */
@ConditionalOnModule("blog")
@Component
public class BlogDraftReaderImpl implements BlogDraftReader {

    @Resource
    private BlogPostMapper blogPostMapper;

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public List<BlogDraftItem> listDraftsByUser(Long userId) {
        List<BlogPost> drafts = blogPostMapper.selectListByQuery(QueryWrapper.create()
                .where("user_id = ?", userId)
                .and("status = ?", 0));
        return drafts.stream()
                .map(post -> new BlogDraftItem(post.getId(), post.getTitle()))
                .collect(Collectors.toList());
    }
}
