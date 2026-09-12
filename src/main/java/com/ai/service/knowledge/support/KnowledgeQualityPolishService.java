package com.ai.service.knowledge.support;

import com.ai.config.ConditionalOnModule;

import cn.hutool.core.util.StrUtil;
import com.ai.constant.AiUsageSceneConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.mapper.knowledge.SourceDocumentMapper;
import com.ai.model.entity.knowledge.SourceDocument;
import com.ai.ops.AiUsageCallContext;
import com.ai.service.knowledge.impl.KnowledgeDeepSeekReadingService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 质量精修（模型分层）：distill 默认 flash，仅「发布博客 / 入库 RAG」前用 pro 精修一版。
 * pro 失败自动降级 flash，再失败原样返回草稿，链路不空转。
 */
@Slf4j
@ConditionalOnModule("knowledge")
@Service
public class KnowledgeQualityPolishService {

    private static final int POLISH_MATERIAL_CHARS = 60_000;

    @Resource
    private KnowledgeDeepSeekReadingService deepSeekReadingService;

    @Resource
    private SourceDocumentMapper sourceDocumentMapper;

    /**
     * 返回精修稿；任一步失败都回退到原稿，不抛业务异常。
     */
    public String polish(Long userId, Long sourceDocumentId, String draftMarkdown, String agentQuery) {
        if (StrUtil.isBlank(draftMarkdown)) {
            return draftMarkdown;
        }
        String material = loadMaterial(sourceDocumentId);
        AiUsageCallContext.set(userId, AiUsageSceneConstant.POLISH, sourceDocumentId, "quality-polish");
        try {
            return deepSeekReadingService.polishForQuality(draftMarkdown, material, agentQuery);
        } catch (BusinessException e) {
            log.warn("Polish failed, keep draft: {}", e.getMessage());
            return draftMarkdown;
        } catch (Exception e) {
            log.warn("Polish unexpected failed, keep draft: {}", e.getMessage());
            return draftMarkdown;
        } finally {
            AiUsageCallContext.clear();
        }
    }

    /** 从 tags 中提取 agentQuery 标记（mergeTags 写入的 agentQuery:xxx） */
    public static String extractAgentQuery(String tags) {
        if (StrUtil.isBlank(tags)) {
            return null;
        }
        for (String tag : tags.split(",")) {
            String t = tag == null ? "" : tag.trim();
            if (t.startsWith("agentQuery:")) {
                String query = t.substring("agentQuery:".length()).trim();
                return query.isBlank() ? null : query;
            }
        }
        return null;
    }

    private String loadMaterial(Long sourceDocumentId) {
        if (sourceDocumentId == null) {
            return null;
        }
        try {
            SourceDocument source = sourceDocumentMapper.selectOneById(sourceDocumentId);
            if (source == null || StrUtil.isBlank(source.getRawText())) {
                return null;
            }
            String raw = source.getRawText();
            return raw.length() > POLISH_MATERIAL_CHARS
                    ? raw.substring(0, POLISH_MATERIAL_CHARS)
                    : raw;
        } catch (Exception e) {
            log.warn("Load polish material failed, sourceId={}", sourceDocumentId, e);
            return null;
        }
    }
}
