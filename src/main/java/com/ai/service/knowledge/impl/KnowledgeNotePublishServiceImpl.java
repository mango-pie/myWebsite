package com.ai.service.knowledge.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.ai.constant.knowledge.KnowledgeNoteConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.mapper.knowledge.KnowledgeNoteMapper;
import com.ai.mapper.knowledge.SourceDocumentMapper;
import com.ai.model.dto.blog.BlogPostAddRequest;
import com.ai.model.dto.blog.BlogPostUpdateRequest;
import com.ai.model.dto.knowledge.KnowledgeNotePublishRequest;
import com.ai.model.entity.BlogPost;
import com.ai.model.entity.knowledge.KnowledgeNote;
import com.ai.model.entity.knowledge.SourceDocument;
import com.ai.model.vo.blog.BlogPostVO;
import com.ai.service.BlogPostService;
import com.ai.service.knowledge.KnowledgeNotePublishService;
import com.ai.service.knowledge.KnowledgeNoteService;
import com.ai.setting.runtime.ReadingRuntimeSettings;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class KnowledgeNotePublishServiceImpl implements KnowledgeNotePublishService {

    @Resource
    private KnowledgeNoteService knowledgeNoteService;

    @Resource
    private KnowledgeNoteMapper knowledgeNoteMapper;

    @Resource
    private SourceDocumentMapper sourceDocumentMapper;

    @Resource
    private BlogPostService blogPostService;

    @Resource
    private ReadingRuntimeSettings readingRuntimeSettings;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BlogPostVO publishToBlog(Long noteId, KnowledgeNotePublishRequest request, Long userId) {
        KnowledgeNote note = knowledgeNoteService.requireOwned(noteId, userId);
        if (note.getBlogPostId() != null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "该精读已关联博客，请使用同步博客接口");
        }
        if (StrUtil.isBlank(note.getDistilledMd())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "精读内容为空，无法发布博客");
        }
        int defaultStatus = readingRuntimeSettings.publishDefaultAsDraft() ? 0 : 1;
        int status = request != null && request.getStatus() != null ? request.getStatus() : defaultStatus;
        if (status != 0 && status != 1) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "发布状态仅支持 0 草稿或 1 发布");
        }

        SourceDocument source = sourceDocumentMapper.selectOneById(note.getSourceDocumentId());
        BlogPostAddRequest addRequest = new BlogPostAddRequest();
        addRequest.setTitle(StrUtil.blankToDefault(note.getTitle(), "未命名精读"));
        addRequest.setContent(note.getDistilledMd());
        addRequest.setSummary(buildSummary(note.getDistilledMd()));
        if (request != null) {
            addRequest.setCategoryId(request.getCategoryId());
            addRequest.setTagIds(request.getTagIds());
        }
        addRequest.setStatus(status);
        addRequest.setExtendInfo(buildExtendInfo(note, source));

        long postId = blogPostService.addBlogPost(addRequest, userId);
        LocalDateTime now = LocalDateTime.now();
        note.setBlogPostId(postId);
        note.setPublishStatus(status == 1
                ? KnowledgeNoteConstant.PUBLISH_PUBLISHED
                : KnowledgeNoteConstant.PUBLISH_DRAFT_CREATED);
        note.setLastPublishedAt(now);
        note.setUpdateTime(now);
        knowledgeNoteMapper.update(note);
        return blogPostService.getBlogPostVO(postId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BlogPostVO syncToBlog(Long noteId, Long userId) {
        KnowledgeNote note = knowledgeNoteService.requireOwned(noteId, userId);
        if (note.getBlogPostId() == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "该精读尚未发布博客，请先发布");
        }
        if (StrUtil.isBlank(note.getDistilledMd())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "精读内容为空，无法同步博客");
        }
        BlogPost post = blogPostService.getById(note.getBlogPostId());
        if (post == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "关联的博客文章不存在");
        }

        BlogPostUpdateRequest updateRequest = new BlogPostUpdateRequest();
        updateRequest.setId(note.getBlogPostId());
        updateRequest.setTitle(StrUtil.blankToDefault(note.getTitle(), post.getTitle()));
        updateRequest.setContent(note.getDistilledMd());
        updateRequest.setSummary(buildSummary(note.getDistilledMd()));
        updateRequest.setStatus(post.getStatus());
        blogPostService.updateBlogPost(updateRequest, userId);

        LocalDateTime now = LocalDateTime.now();
        note.setPublishStatus(Integer.valueOf(1).equals(post.getStatus())
                ? KnowledgeNoteConstant.PUBLISH_PUBLISHED
                : KnowledgeNoteConstant.PUBLISH_DRAFT_CREATED);
        note.setLastPublishedAt(now);
        note.setUpdateTime(now);
        knowledgeNoteMapper.update(note);
        return blogPostService.getBlogPostVO(note.getBlogPostId());
    }

    private String buildSummary(String markdown) {
        if (StrUtil.isBlank(markdown)) {
            return "";
        }
        String plain = markdown.replaceAll("[#>*`\\[\\]()_-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return plain.length() <= 150 ? plain : plain.substring(0, 150);
    }

    private String buildExtendInfo(KnowledgeNote note, SourceDocument source) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("source", "knowledge_note");
        info.put("knowledgeNoteId", note.getId());
        info.put("sourceDocumentId", note.getSourceDocumentId());
        if (source != null) {
            info.put("sourceUrl", source.getSourceUrl());
            info.put("sourceType", source.getSourceType());
        }
        info.put("generatedBy", "KnowledgeAI");
        return JSONUtil.toJsonStr(info);
    }
}
