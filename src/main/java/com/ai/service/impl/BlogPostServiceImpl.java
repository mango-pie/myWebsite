package com.ai.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.mapper.BlogCategoryMapper;
import com.ai.model.dto.blog.BlogPostAddRequest;
import com.ai.model.dto.blog.BlogPostQueryRequest;
import com.ai.model.dto.blog.BlogPostUpdateRequest;
import com.ai.model.entity.BlogCategory;
import com.ai.model.entity.BlogPost;
import com.ai.model.entity.BlogPostTag;
import com.ai.model.entity.BlogTag;
import com.ai.model.entity.User;
import com.ai.model.vo.blog.BlogPostVO;
import com.ai.model.vo.blog.BlogImageVO;
import com.ai.model.vo.blog.BlogTagVO;
import com.ai.service.BlogCategoryService;
import com.ai.service.BlogPostTagService;
import com.ai.service.BlogPostService;
import com.ai.service.BlogTagService;
import com.ai.service.UserService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.util.LambdaGetter;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.ai.mapper.BlogPostMapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class BlogPostServiceImpl extends ServiceImpl<BlogPostMapper, BlogPost> implements BlogPostService {

    @Lazy
    @Resource
    private BlogCategoryService blogCategoryService;

    @Resource
    private BlogTagService blogTagService;

    @Resource
    private BlogPostTagService blogPostTagService;

    @Resource
    private UserService userService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long addBlogPost(BlogPostAddRequest blogPostAddRequest, Long userId) {
        if (blogPostAddRequest == null || userId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        BlogPost blogPost = new BlogPost();
        BeanUtil.copyProperties(blogPostAddRequest, blogPost);
        blogPost.setUserId(userId);
        blogPost.setViewCount(0);
        blogPost.setLikeCount(0);
        blogPost.setCreatedTime(LocalDateTime.now());
        blogPost.setUpdatedTime(LocalDateTime.now());
        
        String extendInfo = blogPost.getExtendInfo();
        if (StrUtil.isBlank(extendInfo)) {
            blogPost.setExtendInfo("{}");
        } else {
            try {
                cn.hutool.json.JSONUtil.parse(extendInfo);
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "extendInfo 必须是有效的 JSON 格式");
            }
        }

        boolean saveResult = this.save(blogPost);
        if (!saveResult) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "创建文章失败");
        }

        if (CollUtil.isNotEmpty(blogPostAddRequest.getTagIds())) {
            addPostTags(blogPost.getId(), blogPostAddRequest.getTagIds());
        }

        return blogPost.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateBlogPost(BlogPostUpdateRequest blogPostUpdateRequest, Long userId) {
        if (blogPostUpdateRequest == null || blogPostUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        BlogPost oldPost = this.getById(blogPostUpdateRequest.getId());
        if (oldPost == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文章不存在");
        }

        if (!oldPost.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限修改此文章");
        }

        BlogPost blogPost = new BlogPost();
        BeanUtil.copyProperties(blogPostUpdateRequest, blogPost);
        blogPost.setUpdatedTime(LocalDateTime.now());
        
        String extendInfo = blogPost.getExtendInfo();
        if (StrUtil.isBlank(extendInfo)) {
            blogPost.setExtendInfo("{}");
        } else {
            try {
                cn.hutool.json.JSONUtil.parse(extendInfo);
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "extendInfo 必须是有效的 JSON 格式");
            }
        }

        boolean updateResult = this.updateById(blogPost);
        if (!updateResult) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "更新文章失败");
        }

        if (blogPostUpdateRequest.getTagIds() != null) {
            blogPostTagService.removeByPostId(blogPostUpdateRequest.getId());
            if (CollUtil.isNotEmpty(blogPostUpdateRequest.getTagIds())) {
                addPostTags(blogPost.getId(), blogPostUpdateRequest.getTagIds());
            }
        }

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteBlogPost(Long id, Long userId) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        BlogPost blogPost = this.getById(id);
        if (blogPost == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文章不存在");
        }

        if (!blogPost.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限删除此文章");
        }

        blogPost.setDeletedTime(LocalDateTime.now());
        boolean deleteResult = this.updateById(blogPost);
        if (!deleteResult) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "删除文章失败");
        }

        blogPostTagService.removeByPostId(id);
        return true;
    }

    @Override
    public boolean updateBlogPostStatus(Long id, Integer status, Long userId) {
        if (id == null || status == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        BlogPost blogPost = this.getById(id);
        if (blogPost == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文章不存在");
        }

        if (!blogPost.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限修改此文章");
        }

        blogPost.setStatus(status);
        blogPost.setUpdatedTime(LocalDateTime.now());
        return this.updateById(blogPost);
    }

    @Override
    public boolean toggleTopStatus(Long id, Integer isTop, Long userId) {
        if (id == null || isTop == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        BlogPost blogPost = this.getById(id);
        if (blogPost == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文章不存在");
        }

        if (!blogPost.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限修改此文章");
        }

        blogPost.setIsTop(isTop);
        blogPost.setUpdatedTime(LocalDateTime.now());
        return this.updateById(blogPost);
    }

    @Override
    public boolean incrementViewCount(Long id) {
        if (id == null) {
            return false;
        }
        return this.mapper.incrementViewCount(id) > 0;
    }

    @Override
    public boolean incrementLikeCount(Long id) {
        if (id == null) {
            return false;
        }
        return this.mapper.incrementLikeCount(id) > 0;
    }

    @Override
    public BlogPostVO getBlogPostVO(Long id) {
        if (id == null) {
            return null;
        }
        BlogPost blogPost = this.getById(id);
        if (blogPost == null) {
            return null;
        }
        return convertToVO(blogPost);
    }

    @Override
    public Page<BlogPostVO> queryBlogPostPage(BlogPostQueryRequest blogPostQueryRequest) {
        if (blogPostQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        int pageNum = blogPostQueryRequest.getPageNum();
        int pageSize = blogPostQueryRequest.getPageSize();
        Page<BlogPost> postPage = this.page(Page.of(pageNum, pageSize), getQueryWrapper(blogPostQueryRequest));

        Page<BlogPostVO> voPage = new Page<>(pageNum, pageSize, postPage.getTotalRow());
        voPage.setRecords(postPage.getRecords().stream().map(this::convertToVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public Page<BlogPostVO> getBlogPostPageByCategory(Long categoryId, int pageNum, int pageSize) {
        QueryWrapper queryWrapper = new QueryWrapper()
                .eq("category_id", categoryId)
                .eq("status", 1)
                .orderBy("is_top", false)
                .orderBy("created_time", false);

        Page<BlogPost> postPage = this.page(Page.of(pageNum, pageSize), queryWrapper);
        Page<BlogPostVO> voPage = new Page<>(pageNum, pageSize, postPage.getTotalRow());
        voPage.setRecords(postPage.getRecords().stream().map(this::convertToVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public Page<BlogPostVO> getBlogPostPageByTag(Long tagId, int pageNum, int pageSize) {
        List<Long> postIds = blogPostTagService.getPostIdsByTagId(tagId);
        if (CollUtil.isEmpty(postIds)) {
            return new Page<>(pageNum, pageSize, 0);
        }

        QueryWrapper queryWrapper = QueryWrapper.create()
                .in("id", postIds)
                .eq("status", 1)
                .orderBy("is_top", false)
                .orderBy("created_time", false);

        Page<BlogPost> postPage = this.page(Page.of(pageNum, pageSize), queryWrapper);
        Page<BlogPostVO> voPage = new Page<>(pageNum, pageSize, postPage.getTotalRow());
        voPage.setRecords(postPage.getRecords().stream().map(this::convertToVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public Page<BlogPostVO> getPublishedBlogPostPage(int pageNum, int pageSize) {
        QueryWrapper queryWrapper = new QueryWrapper()
                .eq("status", 1)
                .isNull("deleted_time")
                .orderBy("is_top", false)
                .orderBy("created_time", false);

        Page<BlogPost> postPage = this.page(Page.of(pageNum, pageSize), queryWrapper);
        Page<BlogPostVO> voPage = new Page<>(pageNum, pageSize, postPage.getTotalRow());
        voPage.setRecords(postPage.getRecords().stream().map(this::convertToVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public QueryWrapper getQueryWrapper(BlogPostQueryRequest blogPostQueryRequest) {
        if (blogPostQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        Long id = blogPostQueryRequest.getId();
        String title = blogPostQueryRequest.getTitle();
        String summary = blogPostQueryRequest.getSummary();
        Long categoryId = blogPostQueryRequest.getCategoryId();
        Long tagId = blogPostQueryRequest.getTagId();
        Long userId = blogPostQueryRequest.getUserId();
        Integer status = blogPostQueryRequest.getStatus();
        Integer isTop = blogPostQueryRequest.getIsTop();
        String searchText = blogPostQueryRequest.getSearchText();
        String sortField = blogPostQueryRequest.getSortField();
        String sortOrder = blogPostQueryRequest.getSortOrder();

        QueryWrapper queryWrapper = QueryWrapper.create()
                .isNull("deleted_time");

        if (id != null) {
            queryWrapper.eq("id", id);
        }
        if (StrUtil.isNotBlank(title)) {
            queryWrapper.like("title", title);
        }
        if (StrUtil.isNotBlank(summary)) {
            queryWrapper.like("summary", summary);
        }
        if (categoryId != null) {
            queryWrapper.eq("category_id", categoryId);
        }
        if (tagId != null) {
            List<Long> postIds = blogPostTagService.getPostIdsByTagId(tagId);
            if (CollUtil.isEmpty(postIds)) {
                queryWrapper.eq("id", -1L);
            } else {
                queryWrapper.in("id", postIds);
            }
        }
        if (userId != null) {
            queryWrapper.eq("user_id", userId);
        }
        if (status != null) {
            queryWrapper.eq("status", status);
        }
        if (isTop != null) {
            queryWrapper.eq("is_top", isTop);
        }

        if (StrUtil.isNotBlank(searchText)) {
            // 通用搜索：标题或摘要（用于管理后台等需要模糊搜索多字段的场景）
            queryWrapper.and("(title LIKE ? OR summary LIKE ?)", "%" + searchText + "%", "%" + searchText + "%");
        }

        if (StrUtil.isNotBlank(sortField)) {
            queryWrapper.orderBy(sortField, "ascend".equals(sortOrder));
        } else {
            queryWrapper.orderBy("is_top", false).orderBy("created_time", false);
        }

        return queryWrapper;
    }

    private void addPostTags(Long postId, List<Long> tagIds) {
        for (Long tagId : tagIds) {
            BlogPostTag postTag = BlogPostTag.builder()
                    .postId(postId)
                    .tagId(tagId)
                    .createdTime(LocalDateTime.now())
                    .build();
            blogPostTagService.save(postTag);

            blogTagService.incrementCount(tagId);
        }
    }

    private BlogPostVO convertToVO(BlogPost blogPost) {
        if (blogPost == null) {
            return null;
        }

        BlogPostVO vo = new BlogPostVO();
        BeanUtil.copyProperties(blogPost, vo);

        vo.setStatusText(getStatusText(blogPost.getStatus()));

        if (blogPost.getCategoryId() != null) {
            BlogCategory category = blogCategoryService.getById(blogPost.getCategoryId());
            if (category != null) {
                vo.setCategoryName(category.getName());
            }
        }

        if (blogPost.getUserId() != null) {
            User user = userService.getById(blogPost.getUserId());
            if (user != null) {
                vo.setUserName(user.getUserName());
                vo.setUserAvatar(user.getUserAvatar());
            }
        }

        List<BlogTag> tags = blogTagService.getTagsByPostId(blogPost.getId());
        if (CollUtil.isNotEmpty(tags)) {
            vo.setTags(tags.stream().map(this::convertTagToVO).collect(Collectors.toList()));
        }

        return vo;
    }

    private BlogTagVO convertTagToVO(BlogTag tag) {
        if (tag == null) {
            return null;
        }
        BlogTagVO vo = new BlogTagVO();
        BeanUtil.copyProperties(tag, vo);
        vo.setStatusText(tag.getStatus() == 1 ? "启用" : "禁用");
        return vo;
    }

    private String getStatusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case 0 -> "草稿";
            case 1 -> "发布";
            case 2 -> "下架";
            default -> "未知";
        };
    }
}