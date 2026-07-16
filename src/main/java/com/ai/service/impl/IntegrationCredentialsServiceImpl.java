package com.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.ai.config.AppDeployProperties;
import com.ai.config.AstrBotProperties;
import com.ai.config.ChatImageCaptionProperties;
import com.ai.config.GptSovitsProperties;
import com.ai.config.knowledge.KnowledgeAiProperties;
import com.ai.config.knowledge.KnowledgeJinaProperties;
import com.ai.config.knowledge.KnowledgeMinioProperties;
import com.ai.config.knowledge.KnowledgeTavilyProperties;
import com.ai.config.knowledge.KnowledgeDeepSeekProperties;
import com.ai.constant.SiteSettingConstant;
import com.ai.service.IntegrationCredentialsService;
import com.ai.service.SiteSettingService;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class IntegrationCredentialsServiceImpl implements IntegrationCredentialsService {

    @Resource
    private SiteSettingService siteSettingService;
    @Resource
    private KnowledgeAiProperties knowledgeAiProperties;
    @Resource
    private KnowledgeJinaProperties knowledgeJinaProperties;
    @Resource
    private KnowledgeTavilyProperties knowledgeTavilyProperties;
    @Resource
    private KnowledgeDeepSeekProperties knowledgeDeepSeekProperties;
    @Resource
    private KnowledgeMinioProperties knowledgeMinioProperties;
    @Resource
    private ChatImageCaptionProperties chatImageCaptionProperties;
    @Resource
    private AstrBotProperties astrBotProperties;
    @Resource
    private GptSovitsProperties gptSovitsProperties;
    @Resource
    private AppDeployProperties appDeployProperties;

    @Value("${langchain4j.open-ai.agent-chat-model.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String agentBaseUrlYaml;
    @Value("${langchain4j.open-ai.agent-chat-model.api-key:}")
    private String agentApiKeyYaml;
    @Value("${langchain4j.open-ai.streaming-chat-model.base-url:}")
    private String codegenBaseUrlYaml;
    @Value("${langchain4j.open-ai.streaming-chat-model.api-key:}")
    private String codegenApiKeyYaml;
    @Value("${upload.path:./tmp/uploads/}")
    private String uploadPathYaml;
    @Value("${upload.base-url:http://localhost:8123/api}")
    private String uploadBaseUrlYaml;

    @Override
    public String knowledgeAiBaseUrl() {
        return overrideOr("knowledge.ai.base_url", knowledgeAiProperties.getBaseUrl());
    }

    @Override
    public String knowledgeAiApiKey() {
        return overrideOr("knowledge.ai.api_key", knowledgeAiProperties.getApiKey());
    }

    @Override
    public String jinaBaseUrl() {
        return overrideOr("knowledge.jina.base_url", knowledgeJinaProperties.getBaseUrl());
    }

    @Override
    public String jinaApiKey() {
        return overrideOr("knowledge.jina.api_key", "");
    }

    @Override
    public String tavilyBaseUrl() {
        return overrideOr("knowledge.tavily.base_url", knowledgeTavilyProperties.getBaseUrl());
    }

    @Override
    public String tavilyApiKey() {
        return overrideOr("knowledge.tavily.api_key", knowledgeTavilyProperties.getApiKey());
    }

    @Override
    public String deepseekBaseUrl() {
        return overrideOr("knowledge.deepseek.base_url", knowledgeDeepSeekProperties.getBaseUrl());
    }

    @Override
    public String deepseekApiKey() {
        return overrideOr("knowledge.deepseek.api_key", knowledgeDeepSeekProperties.getApiKey());
    }

    @Override
    public String agentBaseUrl() {
        return overrideOr("chat.agent.base_url", agentBaseUrlYaml);
    }

    @Override
    public String agentApiKey() {
        String key = overrideOr("chat.agent.api_key", agentApiKeyYaml);
        if (StrUtil.isBlank(key)) {
            return chatImageCaptionProperties.getApiKey();
        }
        return key;
    }

    @Override
    public String imageCaptionBaseUrl() {
        return overrideOr("chat.image_caption.base_url", chatImageCaptionProperties.getBaseUrl());
    }

    @Override
    public String imageCaptionApiKey() {
        return overrideOr("chat.image_caption.api_key", chatImageCaptionProperties.getApiKey());
    }

    @Override
    public String segmentationBaseUrl() {
        String v = overrideOr("chat.segmentation.base_url", "");
        return StrUtil.isBlank(v) ? agentBaseUrl() : v;
    }

    @Override
    public String segmentationApiKey() {
        String v = overrideOr("chat.segmentation.api_key", "");
        return StrUtil.isBlank(v) ? agentApiKey() : v;
    }

    @Override
    public String codegenBaseUrl() {
        return overrideOr("codegen.base_url", codegenBaseUrlYaml);
    }

    @Override
    public String codegenApiKey() {
        return overrideOr("codegen.api_key", codegenApiKeyYaml);
    }

    @Override
    public String astrBotBaseUrl() {
        return overrideOr("astrbot.base_url", astrBotProperties.getBaseUrl());
    }

    @Override
    public String astrBotApiKey() {
        return overrideOr("astrbot.api_key", astrBotProperties.getApiKey());
    }

    @Override
    public String ttsBaseUrl() {
        return overrideOr("tts.base_url", gptSovitsProperties.getBaseUrl());
    }

    @Override
    public String ttsRefAudioUploadDir() {
        String yaml = gptSovitsProperties.getRefAudio() != null
                ? gptSovitsProperties.getRefAudio().getUploadDir() : "./tmp/tts-ref/";
        return overrideOr("tts.ref_audio_upload_dir", yaml);
    }

    @Override
    public String ttsSeedRefAudioPath() {
        String yaml = gptSovitsProperties.getSeedVoice() != null
                ? gptSovitsProperties.getSeedVoice().getRefAudioPath() : "";
        return overrideOr("tts.seed_voice.ref_audio_path", yaml);
    }

    @Override
    public String ttsSeedPromptText() {
        String yaml = gptSovitsProperties.getSeedVoice() != null
                ? gptSovitsProperties.getSeedVoice().getPromptText() : "";
        return overrideOr("tts.seed_voice.prompt_text", yaml);
    }

    @Override
    public String uploadPath() {
        return overrideOr("upload.path", uploadPathYaml);
    }

    @Override
    public String uploadBaseUrl() {
        return overrideOr("upload.base_url", uploadBaseUrlYaml);
    }

    @Override
    public String appDeployHost() {
        return overrideOr("app.deploy.host", appDeployProperties.getHost());
    }

    @Override
    public String appDeployCodeOutputDir() {
        String yaml = appDeployProperties.getCodeOutputDir();
        String override = siteSettingService.findOverride(SiteSettingConstant.MODULE_INTEGRATION, "app.deploy.code_output_dir")
                .orElse(null);
        if (StrUtil.isNotBlank(override)) {
            return override;
        }
        if (StrUtil.isNotBlank(yaml)) {
            return yaml;
        }
        return appDeployProperties.resolveCodeOutputDir();
    }

    @Override
    public String appDeployCodeDeployDir() {
        String yaml = appDeployProperties.getCodeDeployDir();
        String override = siteSettingService.findOverride(SiteSettingConstant.MODULE_INTEGRATION, "app.deploy.code_deploy_dir")
                .orElse(null);
        if (StrUtil.isNotBlank(override)) {
            return override;
        }
        if (StrUtil.isNotBlank(yaml)) {
            return yaml;
        }
        return appDeployProperties.resolveCodeDeployDir();
    }

    @Override
    public boolean minioEnabled() {
        return siteSettingService.findOverride(SiteSettingConstant.MODULE_INTEGRATION, "knowledge.minio.enabled")
                .map(v -> "true".equalsIgnoreCase(v) || "1".equals(v))
                .orElse(knowledgeMinioProperties.isEnabled());
    }

    @Override
    public String minioEndpoint() {
        return overrideOr("knowledge.minio.endpoint", knowledgeMinioProperties.getEndpoint());
    }

    @Override
    public String minioAccessKey() {
        return overrideOr("knowledge.minio.access_key", knowledgeMinioProperties.getAccessKey());
    }

    @Override
    public String minioSecretKey() {
        return overrideOr("knowledge.minio.secret_key", knowledgeMinioProperties.getSecretKey());
    }

    @Override
    public String minioBucketDocuments() {
        return overrideOr("knowledge.minio.bucket_documents", knowledgeMinioProperties.getBucketDocuments());
    }

    private String overrideOr(String key, String fallback) {
        return siteSettingService.findOverride(SiteSettingConstant.MODULE_INTEGRATION, key)
                .filter(StrUtil::isNotBlank)
                .orElse(fallback == null ? "" : fallback);
    }
}
