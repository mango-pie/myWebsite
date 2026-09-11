package com.ai.service.knowledge.spi;

import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.model.vo.blog.BlogPostVO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * blog 模块关闭时的兜底实现；与 BlogNoteBlogPublisher 互斥（blog 开时只注册后者）。
 */
@Component
@ConditionalOnProperty(name = "app.modules.blog", havingValue = "false")
public class NoOpNoteBlogPublisher implements NoteBlogPublisher {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public BlogPostVO publish(NoteBlogPublishCommand command) {
        throw disabled();
    }

    @Override
    public BlogPostVO sync(NoteBlogSyncCommand command) {
        throw disabled();
    }

    @Override
    public BlogPostVO findBlogPost(Long blogPostId) {
        return null;
    }

    @Override
    public void softDelete(Long blogPostId, Long userId) {
        // blog 未启用：无关联文章可删
    }

    private BusinessException disabled() {
        return new BusinessException(ErrorCode.OPERATION_ERROR, "博客模块未启用，无法发布或同步文章");
    }
}