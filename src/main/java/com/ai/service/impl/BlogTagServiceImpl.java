package com.ai.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.model.dto.blog.BlogTagAddRequest;
import com.ai.model.dto.blog.BlogTagQueryRequest;
import com.ai.model.dto.blog.BlogTagUpdateRequest;
import com.ai.model.entity.BlogTag;
import com.ai.model.entity.BlogPostTag;
import com.ai.model.vo.blog.BlogTagVO;
import com.ai.service.BlogPostTagService;
import com.ai.service.BlogTagService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.ai.mapper.BlogTagMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BlogTagServiceImpl extends ServiceImpl<BlogTagMapper, BlogTag> implements BlogTagService {

    @Resource
    private BlogPostTagService blogPostTagService;

    @Override
    public long addTag(BlogTagAddRequest blogTagAddRequest) {
        if (blogTagAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        BlogTag existTag = this.getOne(QueryWrapper.create()
                .where("name = ?", blogTagAddRequest.getName()));
        if (existTag != null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "标签名称已存在");
        }

        BlogTag tag = new BlogTag();
        BeanUtil.copyProperties(blogTagAddRequest, tag);
        tag.setCreatedTime(LocalDateTime.now());
        tag.setUpdatedTime(LocalDateTime.now());
        tag.setCount(0);

        if (tag.getStatus() == null) {
            tag.setStatus(1);
        }
        if (StrUtil.isBlank(tag.getColor())) {
            tag.setColor("#667eea");
        }

        boolean saveResult = this.save(tag);
        if (!saveResult) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "创建标签失败");
        }

        return tag.getId();
    }

    @Override
    public boolean updateTag(BlogTagUpdateRequest blogTagUpdateRequest) {
        if (blogTagUpdateRequest == null || blogTagUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        BlogTag oldTag = this.getById(blogTagUpdateRequest.getId());
        if (oldTag == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "标签不存在");
        }

        if (blogTagUpdateRequest.getName() != null && !oldTag.getName().equals(blogTagUpdateRequest.getName())) {
            BlogTag existTag = this.getOne(QueryWrapper.create()
                    .where("name = ?", blogTagUpdateRequest.getName()));
            if (existTag != null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "标签名称已存在");
            }
        }

        BlogTag tag = new BlogTag();
        BeanUtil.copyProperties(blogTagUpdateRequest, tag);
        tag.setUpdatedTime(LocalDateTime.now());

        boolean updateResult = this.updateById(tag);
        if (!updateResult) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "更新标签失败");
        }

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteTag(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        BlogTag tag = this.getById(id);
        if (tag == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "标签不存在");
        }

        boolean deleteResult = this.removeById(id);
        if (!deleteResult) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "删除标签失败");
        }

        blogPostTagService.removeByTagId(id);
        return true;
    }

    @Override
    public BlogTagVO getTagVO(Long id) {
        if (id == null) {
            return null;
        }
        BlogTag tag = this.getById(id);
        if (tag == null) {
            return null;
        }
        return convertToVO(tag);
    }

    @Override
    public Page<BlogTagVO> queryTagPage(BlogTagQueryRequest blogTagQueryRequest) {
        if (blogTagQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        int pageNum = blogTagQueryRequest.getPageNum();
        int pageSize = blogTagQueryRequest.getPageSize();
        Page<BlogTag> tagPage = this.page(Page.of(pageNum, pageSize), getQueryWrapper(blogTagQueryRequest));

        Page<BlogTagVO> voPage = new Page<>(pageNum, pageSize, tagPage.getTotalRow());
        voPage.setRecords(tagPage.getRecords().stream().map(this::convertToVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public QueryWrapper getQueryWrapper(BlogTagQueryRequest blogTagQueryRequest) {
        if (blogTagQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        Long id = blogTagQueryRequest.getId();
        String name = blogTagQueryRequest.getName();
        Integer status = blogTagQueryRequest.getStatus();
        String searchText = blogTagQueryRequest.getSearchText();
        String sortField = blogTagQueryRequest.getSortField();
        String sortOrder = blogTagQueryRequest.getSortOrder();

        QueryWrapper queryWrapper = QueryWrapper.create();

        if (id != null) {
            queryWrapper.eq("id", id);
        }
        if (StrUtil.isNotBlank(name)) {
            queryWrapper.like("name", name);
        }
        if (status != null) {
            queryWrapper.eq("status", status);
        }

        if (StrUtil.isNotBlank(searchText)) {
            queryWrapper.and("(name LIKE ? OR description LIKE ?)", "%" + searchText + "%", "%" + searchText + "%");
        }

        queryWrapper.orderBy(sortField, "ascend".equals(sortOrder));
        queryWrapper.orderBy("count", false);

        return queryWrapper;
    }

    @Override
    public List<BlogTag> getTagsByPostId(Long postId) {
        if (postId == null) {
            return Collections.emptyList();
        }

        List<BlogPostTag> postTags = blogPostTagService.list(QueryWrapper.create()
                .where("post_id = ?", postId));

        if (CollUtil.isEmpty(postTags)) {
            return Collections.emptyList();
        }

        List<Long> tagIds = postTags.stream()
                .map(BlogPostTag::getTagId)
                .collect(Collectors.toList());

        return this.list(QueryWrapper.create()
                .in("id", tagIds)
                .eq("status", 1));
    }

    @Override
    public boolean incrementCount(Long tagId) {
        if (tagId == null) {
            return false;
        }
        return this.mapper.incrementCount(tagId) > 0;
    }

    @Override
    public boolean decrementCount(Long tagId) {
        if (tagId == null) {
            return false;
        }
        return this.mapper.decrementCount(tagId) > 0;
    }

    private BlogTagVO convertToVO(BlogTag tag) {
        if (tag == null) {
            return null;
        }

        BlogTagVO vo = new BlogTagVO();
        BeanUtil.copyProperties(tag, vo);
        vo.setStatusText(tag.getStatus() == 1 ? "启用" : "禁用");

        return vo;
    }
}