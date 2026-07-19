package com.ai.service.study.spi;

import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * blog 模块关闭时的兜底实现：同步草稿明确报错。
 * 与 {@link com.ai.service.blog.BlogDraftReaderImpl} 互斥（blog 开时注册后者）。
 */
@Component
@ConditionalOnProperty(name = "app.modules.blog", havingValue = "false")
public class NoOpBlogDraftReader implements BlogDraftReader {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public List<BlogDraftItem> listDraftsByUser(Long userId) {
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "博客模块未启用，无法同步博客草稿");
    }
}
