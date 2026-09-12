package com.ai.service.knowledge.impl;

import com.ai.config.ConditionalOnModule;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.ai.constant.OpsAuditActionConstant;
import com.ai.constant.knowledge.KnowledgeNoteConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.mapper.knowledge.KnowledgeNoteMapper;
import com.ai.mapper.knowledge.SourceDocumentMapper;
import com.ai.model.dto.knowledge.KnowledgeNotePublishRequest;
import com.ai.model.entity.knowledge.KnowledgeNote;
import com.ai.model.entity.knowledge.SourceDocument;
import com.ai.model.vo.blog.BlogPostVO;
import com.ai.service.OpsAuditLogService;
import com.ai.service.knowledge.KnowledgeNotePublishService;
import com.ai.service.knowledge.KnowledgeNoteService;
import com.ai.service.knowledge.spi.NoteBlogPublishCommand;
import com.ai.service.knowledge.spi.NoteBlogPublisher;
import com.ai.service.knowledge.spi.NoteBlogSyncCommand;
import com.ai.service.knowledge.support.KnowledgeQualityPolishService;
import com.ai.setting.runtime.ReadingRuntimeSettings;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@ConditionalOnModule("knowledge")
@Service
public class KnowledgeNotePublishServiceImpl implements KnowledgeNotePublishService {

    @Resource
    private KnowledgeNoteService knowledgeNoteService;

    @Resource
    private KnowledgeNoteMapper knowledgeNoteMapper;

    @Resource
    private SourceDocumentMapper sourceDocumentMapper;

    @Resource
    private NoteBlogPublisher noteBlogPublisher;

    @Resource
    private ReadingRuntimeSettings readingRuntimeSettings;

    @Resource
    private KnowledgeQualityPolishService polishService;

    @Resource
    private OpsAuditLogService opsAuditLogService;

    @Resource
    private TransactionTemplate transactionTemplate;

    private void requireBlogAvailable() {
        if (!noteBlogPublisher.isAvailable()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "博客模块未启用，无法发布或同步博客");
        }
    }

    @Override
    public BlogPostVO publishToBlog(Long noteId, KnowledgeNotePublishRequest request, Long userId) {
        requireBlogAvailable();
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
        // 模型分层：发布是唯一需要质量的地方，pro 精修一版（失败自动回退原稿）
        // AI 润色是外部 HTTP 调用，不放进事务；仅「发布博客+回写笔记」用短事务保证原子性
        String publishContent = polishService.polish(
                userId, note.getSourceDocumentId(), note.getDistilledMd(),
                KnowledgeQualityPolishService.extractAgentQuery(note.getTags()));
        // 版权合规：精读稿是二次创作，发布时强制附原文链接，避免大段照搬无出处
        publishContent = appendSourceLink(publishContent, note, source);
        NoteBlogPublishCommand command = NoteBlogPublishCommand.builder()
                .title(note.getTitle())
                .contentMd(publishContent)
                .summary(buildSummary(publishContent))
                .categoryId(request != null ? request.getCategoryId() : null)
                .tagIds(request != null ? request.getTagIds() : null)
                .status(status)
                .extendInfo(buildExtendInfo(note, source))
                .userId(userId)
                .build();
        BlogPostVO blogPost = transactionTemplate.execute(tx -> {
            BlogPostVO post = noteBlogPublisher.publish(command);
            LocalDateTime now = LocalDateTime.now();
            note.setBlogPostId(post.getId());
            note.setPublishStatus(status == 1
                    ? KnowledgeNoteConstant.PUBLISH_PUBLISHED
                    : KnowledgeNoteConstant.PUBLISH_DRAFT_CREATED);
            note.setLastPublishedAt(now);
            note.setUpdateTime(now);
            knowledgeNoteMapper.update(note);
            return post;
        });
        auditPublish(note, blogPost.getId(), status, userId);
        return blogPost;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BlogPostVO syncToBlog(Long noteId, Long userId) {
        requireBlogAvailable();
        KnowledgeNote note = knowledgeNoteService.requireOwned(noteId, userId);
        if (note.getBlogPostId() == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "该精读尚未发布博客，请先发布");
        }
        if (StrUtil.isBlank(note.getDistilledMd())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "精读内容为空，无法同步博客");
        }
        NoteBlogSyncCommand command = NoteBlogSyncCommand.builder()
                .blogPostId(note.getBlogPostId())
                .fallbackTitle(note.getTitle())
                .contentMd(note.getDistilledMd())
                .summary(buildSummary(note.getDistilledMd()))
                .userId(userId)
                .build();
        BlogPostVO blogPost = noteBlogPublisher.sync(command);

        LocalDateTime now = LocalDateTime.now();
        note.setPublishStatus(Integer.valueOf(1).equals(blogPost.getStatus())
                ? KnowledgeNoteConstant.PUBLISH_PUBLISHED
                : KnowledgeNoteConstant.PUBLISH_DRAFT_CREATED);
        note.setLastPublishedAt(now);
        note.setUpdateTime(now);
        knowledgeNoteMapper.update(note);
        auditSync(note, userId);
        return blogPost;
    }

    private void auditPublish(KnowledgeNote note, Long blogPostId, int status, Long userId) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("noteId", String.valueOf(note.getId()));
        detail.put("blogPostId", String.valueOf(blogPostId));
        detail.put("status", status == 1 ? "PUBLISHED" : "DRAFT");
        opsAuditLogService.audit(
                OpsAuditActionConstant.READING_NOTE_PUBLISH,
                userId,
                OpsAuditActionConstant.RESOURCE_READING_NOTE,
                String.valueOf(note.getId()),
                true,
                detail,
                null);
    }

    private void auditSync(KnowledgeNote note, Long userId) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("noteId", String.valueOf(note.getId()));
        detail.put("blogPostId", String.valueOf(note.getBlogPostId()));
        opsAuditLogService.audit(
                OpsAuditActionConstant.READING_NOTE_SYNC,
                userId,
                OpsAuditActionConstant.RESOURCE_READING_NOTE,
                String.valueOf(note.getId()),
                true,
                detail,
                null);
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

    /**
     * 版权合规：正文末尾附原文链接（开关 publish.force_source_link，默认开）。
     * 已有原文链接时不重复追加。
     */
    private String appendSourceLink(String content, KnowledgeNote note, SourceDocument source) {
        if (!readingRuntimeSettings.publishForceSourceLink()) {
            return content;
        }
        String sourceUrl = source == null ? null : source.getSourceUrl();
        if (StrUtil.isBlank(sourceUrl)) {
            return content;
        }
        if (content != null && content.contains(sourceUrl)) {
            return content;
        }
        String title = StrUtil.blankToDefault(note.getTitle(), "原文");
        String link = "\n\n---\n\n> 本文为 AI 精读笔记（摘要 + 转述 + 个人理解），原文：["
                + title.replaceAll("\\r?\\n", " ").trim() + "](" + sourceUrl + ")";
        return content == null ? link : content + link;
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
