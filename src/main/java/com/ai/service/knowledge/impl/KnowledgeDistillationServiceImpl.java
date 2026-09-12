package com.ai.service.knowledge.impl;

import com.ai.config.ConditionalOnModule;

import cn.hutool.core.util.StrUtil;
import com.ai.constant.AiUsageSceneConstant;
import com.ai.constant.BizStatMetricConstant;
import com.ai.constant.OpsAuditActionConstant;
import com.ai.constant.knowledge.KnowledgeNoteConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.mapper.knowledge.KnowledgeNoteMapper;
import com.ai.mapper.knowledge.SourceDocumentMapper;
import com.ai.model.entity.knowledge.KnowledgeNote;
import com.ai.model.entity.knowledge.SourceDocument;
import com.ai.ops.AiUsageCallContext;
import com.ai.service.BizStatDailyService;
import com.ai.service.OpsAuditLogService;
import com.ai.service.knowledge.KnowledgeAiModelService;
import com.ai.service.knowledge.KnowledgeDistillationService;
import com.ai.service.knowledge.support.KnowledgeAlignmentChecker;
import com.ai.setting.runtime.ReadingRuntimeSettings;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@ConditionalOnModule("knowledge")
@Service
public class KnowledgeDistillationServiceImpl implements KnowledgeDistillationService {

    private static final int MAX_RAW_CHARS = 60_000;

    @Resource
    private SourceDocumentMapper sourceDocumentMapper;

    @Resource
    private KnowledgeNoteMapper knowledgeNoteMapper;

    @Resource
    private KnowledgeAiModelService aiModelService;

    @Resource
    private KnowledgeAlignmentChecker alignmentChecker;

    @Resource
    private ReadingRuntimeSettings readingRuntimeSettings;

    @Resource
    private BizStatDailyService bizStatDailyService;

    @Resource
    private OpsAuditLogService opsAuditLogService;

    @Override
    public KnowledgeNote distillToMarkdown(Long sourceDocumentId, Long userId) {
        return distillToMarkdown(sourceDocumentId, userId, null);
    }

    @Override
    public KnowledgeNote distillToMarkdown(Long sourceDocumentId, Long userId, String tags) {
        SourceDocument source = requireOwnedSource(sourceDocumentId, userId);
        if (StrUtil.isBlank(source.getRawText())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "源文档原文为空，无法精炼");
        }
        LocalDateTime now = LocalDateTime.now();
        try {
            String markdown = callDistill(source.getRawText(), userId, sourceDocumentId);
            KnowledgeNote note = new KnowledgeNote();
            note.setSourceDocumentId(source.getId());
            note.setTitle(source.getTitle());
            note.setDistilledMd(markdown);
            note.setTags(tags);
            note.setStatus(KnowledgeNoteConstant.STATUS_SUCCESS);
            note.setPublishStatus(KnowledgeNoteConstant.PUBLISH_NOT_PUBLISHED);
            note.setIndexStatus(KnowledgeNoteConstant.INDEX_NOT_INDEXED);
            note.setViewCount(0);
            note.setLastDistilledAt(now);
            note.setCreateTime(now);
            note.setUpdateTime(now);
            note.setIsDelete(0);
            // 对齐度自检（flash，低成本）：URL/文件单篇蒸馏与合蒸链路一致，低分提示换材料 / 重蒸
            String goal = extractAgentQuery(tags);
            if (goal == null) {
                goal = source.getTitle();
            }
            KnowledgeAlignmentChecker.AlignmentResult alignment =
                    alignmentChecker.check(markdown, goal, userId, sourceDocumentId);
            if (alignment != null) {
                note.setAlignmentScore(java.math.BigDecimal.valueOf(alignment.score()));
                note.setAlignmentNote(alignment.note());
            }
            knowledgeNoteMapper.insert(note);
            bizStatDailyService.increment(BizStatMetricConstant.READING_DISTILL_SUCCESS, 1);
            return note;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 精炼失败：" + e.getMessage());
        }
    }

    @Override
    public KnowledgeNote redistill(Long noteId, Long userId) {
        KnowledgeNote note = knowledgeNoteMapper.selectOneById(noteId);
        if (note == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "精读笔记不存在");
        }
        SourceDocument source = requireOwnedSource(note.getSourceDocumentId(), userId);
        if (StrUtil.isBlank(source.getRawText())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "源文档原文为空，无法重新蒸馏");
        }
        LocalDateTime now = LocalDateTime.now();
        // 差量返工：用户编辑过（lastEditedAt 晚于最近蒸馏）时，把当前稿作为「参考草稿」传入，
        // 只重蒸被编辑过的部分，保留人工修改，避免整篇重跑带来的质量回退
        boolean draftReference = note.getLastEditedAt() != null
                && (note.getLastDistilledAt() == null || note.getLastEditedAt().isAfter(note.getLastDistilledAt()));
        String markdown = callDistill(source.getRawText(), note.getDistilledMd(), draftReference, userId, noteId);
        KnowledgeAlignmentChecker.AlignmentResult alignment =
                alignmentChecker.check(markdown, note.getTitle(), userId, note.getSourceDocumentId());
        if (alignment != null) {
            note.setAlignmentScore(java.math.BigDecimal.valueOf(alignment.score()));
            note.setAlignmentNote(alignment.note());
        }
        note.setDistilledMd(markdown);
        note.setTitle(StrUtil.blankToDefault(note.getTitle(), source.getTitle()));
        note.setStatus(KnowledgeNoteConstant.STATUS_SUCCESS);
        note.setErrorMsg(null);
        note.setLastDistilledAt(now);
        note.setUpdateTime(now);
        if (note.getBlogPostId() != null) {
            note.setPublishStatus(KnowledgeNoteConstant.PUBLISH_SYNC_REQUIRED);
        }
        if (note.getKnowledgeDocumentId() != null) {
            note.setIndexStatus(KnowledgeNoteConstant.INDEX_REINDEX_REQUIRED);
        }
        knowledgeNoteMapper.update(note);
        bizStatDailyService.increment(BizStatMetricConstant.READING_DISTILL_SUCCESS, 1);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("noteId", String.valueOf(noteId));
        detail.put("draftReference", String.valueOf(draftReference));
        opsAuditLogService.audit(
                OpsAuditActionConstant.READING_NOTE_REDISTILL,
                userId,
                OpsAuditActionConstant.RESOURCE_READING_NOTE,
                String.valueOf(noteId),
                true,
                detail,
                null);
        return note;
    }

    @Override
    public KnowledgeNote retryDistillation(Long sourceDocumentId, Long userId) {
        KnowledgeNote existing = knowledgeNoteMapper.selectOneByQuery(
                QueryWrapper.create()
                        .eq("source_document_id", sourceDocumentId)
                        .orderBy("create_time", false)
                        .limit(1)
        );
        if (existing != null) {
            return redistill(existing.getId(), userId);
        }
        return distillToMarkdown(sourceDocumentId, userId);
    }

    private String callDistill(String rawText, Long userId, Long conversationId) {
        return callDistill(rawText, null, false, userId, conversationId);
    }

    private String callDistill(String rawText, String draftMd, boolean draftReference,
                               Long userId, Long conversationId) {
        String content = rawText.length() > MAX_RAW_CHARS ? rawText.substring(0, MAX_RAW_CHARS) : rawText;
        String systemPrompt = readingRuntimeSettings.distillSystemPrompt();
        String userPrompt = "原文：\n" + content;
        if (draftReference && StrUtil.isNotBlank(draftMd)) {
            userPrompt += "\n\n参考草稿（保留其中用户人工修改过的表述；与原文不符的事实按原文修正，结构尽量沿用草稿）：\n"
                    + (draftMd.length() > MAX_RAW_CHARS ? draftMd.substring(0, MAX_RAW_CHARS) : draftMd);
        }
        AiUsageCallContext.set(userId, AiUsageSceneConstant.DISTILL, conversationId, "redistill");
        try {
            return aiModelService.chat(
                    systemPrompt,
                    userPrompt,
                    readingRuntimeSettings.distillTemperature(),
                    readingRuntimeSettings.distillMaxTokens()
            );
        } finally {
            AiUsageCallContext.clear();
        }
    }

    private SourceDocument requireOwnedSource(Long sourceDocumentId, Long userId) {
        SourceDocument source = sourceDocumentMapper.selectOneByQuery(QueryWrapper.create().eq("id", sourceDocumentId));
        if (source == null || !userId.equals(source.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "源文档不存在");
        }
        return source;
    }

    /** 从 tags 中解析 agentQuery:xxx 标记（URL/AGENT 采集时由控制器写入），无标记返回 null */
    private String extractAgentQuery(String tags) {
        if (StrUtil.isBlank(tags)) {
            return null;
        }
        for (String tag : tags.split(",")) {
            String t = tag == null ? "" : tag.trim();
            if (t.startsWith("agentQuery:")) {
                String goal = t.substring("agentQuery:".length()).trim();
                return StrUtil.isBlank(goal) ? null : goal;
            }
        }
        return null;
    }
}
