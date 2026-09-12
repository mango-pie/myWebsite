package com.ai.service.impl;

import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.model.entity.BlogPostTag;
import com.ai.service.BlogPostTagService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.ai.mapper.BlogPostTagMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BlogPostTagServiceImpl extends ServiceImpl<BlogPostTagMapper, BlogPostTag> implements BlogPostTagService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addPostTag(Long postId, Long tagId) {
        if (postId == null || tagId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        if (existsPostTag(postId, tagId)) {
            return true;
        }

        BlogPostTag postTag = new BlogPostTag();
        postTag.setPostId(postId);
        postTag.setTagId(tagId);
        postTag.setCreatedTime(LocalDateTime.now());

        return this.save(postTag);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeByPostId(Long postId) {
        if (postId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        QueryWrapper queryWrapper = new QueryWrapper()
                .eq("post_id", postId);

        return this.remove(queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeByTagId(Long tagId) {
        if (tagId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        QueryWrapper queryWrapper = new QueryWrapper()
                .eq("tag_id", tagId);

        return this.remove(queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removePostTag(Long postId, Long tagId) {
        if (postId == null || tagId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        QueryWrapper queryWrapper = new QueryWrapper()
                .eq("post_id", postId)
                .eq("tag_id", tagId);

        return this.remove(queryWrapper);
    }

    @Override
    public List<Long> getTagIdsByPostId(Long postId) {
        if (postId == null) {
            return Collections.emptyList();
        }

        List<BlogPostTag> postTags = this.list(QueryWrapper.create()
                .where("post_id = ?", postId));

        if (postTags.isEmpty()) {
            return Collections.emptyList();
        }

        return postTags.stream()
                .map(BlogPostTag::getTagId)
                .collect(Collectors.toList());
    }

    @Override
    public List<Long> getPostIdsByTagId(Long tagId) {
        if (tagId == null) {
            return Collections.emptyList();
        }

        List<BlogPostTag> postTags = this.list(QueryWrapper.create()
                .where("tag_id = ?", tagId));

        if (postTags.isEmpty()) {
            return Collections.emptyList();
        }

        return postTags.stream()
                .map(BlogPostTag::getPostId)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsPostTag(Long postId, Long tagId) {
        if (postId == null || tagId == null) {
            return false;
        }

        QueryWrapper queryWrapper = new QueryWrapper()
                .eq("post_id", postId)
                .eq("tag_id", tagId);

        return this.count(queryWrapper) > 0;
    }
}