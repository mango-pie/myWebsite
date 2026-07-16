package com.ai.setting.runtime;

import com.ai.config.knowledge.KnowledgeAiProperties;
import com.ai.config.knowledge.KnowledgeJinaProperties;
import com.ai.config.knowledge.KnowledgeRagProperties;
import com.ai.constant.SiteSettingConstant;
import com.ai.service.SiteSettingService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * knowledge 模块运行时读取：DB 覆盖 YAML Properties。
 */
@Component
public class KnowledgeRuntimeSettings {

    @Resource
    private SiteSettingService siteSettingService;
    @Resource
    private KnowledgeAiProperties aiProperties;
    @Resource
    private KnowledgeRagProperties ragProperties;
    @Resource
    private KnowledgeJinaProperties jinaProperties;

    public String chatModel() {
        return siteSettingService.getString(SiteSettingConstant.MODULE_KNOWLEDGE, "ai.chat_model",
                aiProperties.getChatModel());
    }

    public String embeddingModel() {
        return siteSettingService.getString(SiteSettingConstant.MODULE_KNOWLEDGE, "ai.embedding_model",
                aiProperties.getEmbeddingModel());
    }

    public int embeddingDimension() {
        int yaml = aiProperties.getEmbeddingDimension() == null ? 1536 : aiProperties.getEmbeddingDimension();
        return siteSettingService.getInt(SiteSettingConstant.MODULE_KNOWLEDGE, "ai.embedding_dimension", yaml);
    }

    public double temperature() {
        double yaml = aiProperties.getTemperature() == null ? 0.2 : aiProperties.getTemperature();
        return siteSettingService.getDouble(SiteSettingConstant.MODULE_KNOWLEDGE, "ai.temperature", yaml);
    }

    public int maxTokens() {
        int yaml = aiProperties.getMaxTokens() == null ? 2048 : aiProperties.getMaxTokens();
        return siteSettingService.getInt(SiteSettingConstant.MODULE_KNOWLEDGE, "ai.max_tokens", yaml);
    }

    public int timeoutSeconds() {
        int yaml = aiProperties.getTimeoutSeconds() == null ? 120 : aiProperties.getTimeoutSeconds();
        return siteSettingService.getInt(SiteSettingConstant.MODULE_KNOWLEDGE, "ai.timeout_seconds", yaml);
    }

    public int ragTopK() {
        return siteSettingService.getInt(SiteSettingConstant.MODULE_KNOWLEDGE, "rag.top_k",
                ragProperties.getTopK());
    }

    public int ragChunkSize() {
        return siteSettingService.getInt(SiteSettingConstant.MODULE_KNOWLEDGE, "rag.chunk_size",
                ragProperties.getChunkSize());
    }

    public int ragChunkOverlap() {
        return siteSettingService.getInt(SiteSettingConstant.MODULE_KNOWLEDGE, "rag.chunk_overlap",
                ragProperties.getChunkOverlap());
    }

    public int jinaTimeoutSeconds() {
        return siteSettingService.getInt(SiteSettingConstant.MODULE_KNOWLEDGE, "jina.timeout_seconds",
                jinaProperties.getTimeoutSeconds());
    }
}
