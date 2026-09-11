package com.ai.service;

import com.ai.model.dto.blog.BlogPostAddRequest;
import com.ai.model.dto.blog.BlogPostQueryRequest;
import com.ai.model.dto.blog.BlogPostUpdateRequest;
import com.ai.model.entity.BlogPost;
import com.ai.model.entity.User;
import com.ai.model.vo.blog.BlogPostVO;
import com.mybatisflex.core.service.IService;
import com.mybatisflex.core.paginate.Page;

public interface BlogPostService extends IService<BlogPost> {

    long addBlogPost(BlogPostAddRequest blogPostAddRequest, Long userId);

    boolean updateBlogPost(BlogPostUpdateRequest blogPostUpdateRequest, Long userId);

    boolean deleteBlogPost(Long id, Long userId);

    boolean updateBlogPostStatus(Long id, Integer status, Long userId);

    boolean toggleTopStatus(Long id, Integer isTop, Long userId);

    boolean incrementViewCount(Long id);

    boolean incrementLikeCount(Long id);

    /** 内部可信调用：不做公开可见性过滤。 */
    BlogPostVO getBlogPostVO(Long id);

    /** 公开/管理查询：未发布内容仅作者或管理员可见，其余视为不存在。 */
    BlogPostVO getBlogPostVO(Long id, User viewer);

    Page<BlogPostVO> queryBlogPostPage(BlogPostQueryRequest blogPostQueryRequest, User viewer);

    Page<BlogPostVO> getBlogPostPageByCategory(Long categoryId, int pageNum, int pageSize);

    Page<BlogPostVO> getBlogPostPageByTag(Long tagId, int pageNum, int pageSize);

    Page<BlogPostVO> getPublishedBlogPostPage(int pageNum, int pageSize);

    com.mybatisflex.core.query.QueryWrapper getQueryWrapper(BlogPostQueryRequest blogPostQueryRequest);
}
