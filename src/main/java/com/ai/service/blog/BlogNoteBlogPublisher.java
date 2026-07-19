package com.ai.service.blog;

import cn.hutool.core.util.StrUtil;
import com.ai.config.ConditionalOnModule;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.model.dto.blog.BlogPostAddRequest;
import com.ai.model.dto.blog.BlogPostUpdateRequest;
import com.ai.model.entity.BlogPost;
import com.ai.model.vo.blog.BlogPostVO;
import com.ai.service.BlogPostService;
import com.ai.service.knowledge.spi.NoteBlogPublishCommand;
import com.ai.service.knowledge.spi.NoteBlogPublisher;
import com.ai.service.knowledge.spi.NoteBlogSyncCommand;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * blog 模块提供的 {@link NoteBlogPublisher} 真实实现，仅在 blog 模块启用时注册。
 * 所有 blog 侧的请求组装与落库都收敛在此处，knowledge 不再直接依赖 {@link BlogPostService}。
 */
@ConditionalOnModule("blog")
@Component
public class BlogNoteBlogPublisher implements NoteBlogPublisher {

    @Resource
    private BlogPostService blogPostService;

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public BlogPostVO publish(NoteBlogPublishCommand command) {
        BlogPostAddRequest addRequest = new BlogPostAddRequest();
        addRequest.setTitle(StrUtil.blankToDefault(command.getTitle(), "未命名精读"));
        addRequest.setContent(command.getContentMd());
        addRequest.setSummary(command.getSummary());
        addRequest.setCategoryId(command.getCategoryId());
        addRequest.setTagIds(command.getTagIds());
        addRequest.setStatus(command.getStatus());
        addRequest.setExtendInfo(command.getExtendInfo());
        long postId = blogPostService.addBlogPost(addRequest, command.getUserId());
        return blogPostService.getBlogPostVO(postId);
    }

    @Override
    public BlogPostVO sync(NoteBlogSyncCommand command) {
        BlogPost post = blogPostService.getById(command.getBlogPostId());
        if (post == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "关联的博客文章不存在");
        }
        BlogPostUpdateRequest updateRequest = new BlogPostUpdateRequest();
        updateRequest.setId(command.getBlogPostId());
        updateRequest.setTitle(StrUtil.blankToDefault(command.getFallbackTitle(), post.getTitle()));
        updateRequest.setContent(command.getContentMd());
        updateRequest.setSummary(command.getSummary());
        updateRequest.setStatus(post.getStatus());
        blogPostService.updateBlogPost(updateRequest, command.getUserId());
        return blogPostService.getBlogPostVO(command.getBlogPostId());
    }

    @Override
    public BlogPostVO findBlogPost(Long blogPostId) {
        try {
            return blogPostService.getBlogPostVO(blogPostId);
        } catch (Exception ignored) {
            return null;
        }
    }
}
