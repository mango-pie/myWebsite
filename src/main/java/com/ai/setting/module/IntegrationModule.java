package com.ai.setting.module;

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
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.setting.IntegrationClientCache;
import com.ai.setting.SettingFieldSchema;
import com.ai.setting.SettingModule;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * P1：AI / 外部服务连接、密钥与业务路径。
 */
@Component
public class IntegrationModule implements SettingModule {

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

    @Resource
    private IntegrationClientCache integrationClientCache;

    @Value("${langchain4j.open-ai.agent-chat-model.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String agentBaseUrl;
    @Value("${langchain4j.open-ai.streaming-chat-model.base-url:}")
    private String codegenBaseUrl;
    @Value("${upload.path:./tmp/uploads/}")
    private String uploadPath;
    @Value("${upload.base-url:http://localhost:8123/api}")
    private String uploadBaseUrl;

    @Override
    public String code() {
        return SiteSettingConstant.MODULE_INTEGRATION;
    }

    @Override
    public String displayName() {
        return "集成与密钥";
    }

    @Override
    public String phase() {
        return "P1";
    }

    @Override
    public List<SettingFieldSchema> schema() {
        return List.of(
                str("knowledge.ai.base_url", "知识库 AI Base URL", knowledgeAiProperties.getBaseUrl(), false, false),
                str("knowledge.ai.api_key", "知识库 AI API Key", "", true, false),

                str("knowledge.jina.base_url", "Jina Base URL", knowledgeJinaProperties.getBaseUrl(), false, false),
                str("knowledge.jina.api_key", "Jina API Key", "", true, false),

                str("knowledge.tavily.base_url", "Tavily Base URL", knowledgeTavilyProperties.getBaseUrl(), false, false),
                str("knowledge.tavily.api_key", "Tavily API Key", "", true, false),

                str("knowledge.deepseek.base_url", "DeepSeek Anthropic Base URL", knowledgeDeepSeekProperties.getBaseUrl(), false, false),
                str("knowledge.deepseek.api_key", "DeepSeek 官方 API Key（联网搜索）", "", true, false),

                str("chat.agent.base_url", "Agent Base URL", agentBaseUrl, false, false),
                str("chat.agent.api_key", "Agent API Key", "", true, false),
                str("chat.image_caption.base_url", "图片转述 Base URL", chatImageCaptionProperties.getBaseUrl(), false, false),
                str("chat.image_caption.api_key", "图片转述 API Key", "", true, false),
                str("chat.segmentation.base_url", "分段模型 Base URL（可空复用 Agent）", "", false, false),
                str("chat.segmentation.api_key", "分段模型 API Key（可空复用 Agent）", "", true, false),

                str("codegen.base_url", "代码生成 Base URL", codegenBaseUrl, false, false),
                str("codegen.api_key", "代码生成 API Key", "", true, false),

                str("astrbot.base_url", "AstrBot Base URL", astrBotProperties.getBaseUrl(), false, false),
                str("astrbot.api_key", "AstrBot API Key", "", true, false),

                str("tts.base_url", "GPT-SoVITS Base URL", gptSovitsProperties.getBaseUrl(), false, false),
                path("tts.ref_audio_upload_dir", "TTS 参考音频目录",
                        gptSovitsProperties.getRefAudio() != null ? gptSovitsProperties.getRefAudio().getUploadDir() : "./tmp/tts-ref/"),
                path("tts.seed_voice.ref_audio_path", "Seed 参考音频路径",
                        gptSovitsProperties.getSeedVoice() != null ? nullToEmpty(gptSovitsProperties.getSeedVoice().getRefAudioPath()) : ""),
                str("tts.seed_voice.prompt_text", "Seed 提示文本",
                        gptSovitsProperties.getSeedVoice() != null ? nullToEmpty(gptSovitsProperties.getSeedVoice().getPromptText()) : "", false, false),

                path("upload.path", "上传物理目录", uploadPath),
                str("upload.base_url", "上传访问 Base URL", uploadBaseUrl, false, false),
                str("app.deploy.host", "部署 Host", appDeployProperties.getHost(), false, false),
                path("app.deploy.code_output_dir", "代码输出目录", nullToEmpty(appDeployProperties.getCodeOutputDir())),
                path("app.deploy.code_deploy_dir", "代码部署目录", nullToEmpty(appDeployProperties.getCodeDeployDir())),

                bool("knowledge.minio.enabled", "启用 MinIO", knowledgeMinioProperties.isEnabled()),
                str("knowledge.minio.endpoint", "MinIO Endpoint", knowledgeMinioProperties.getEndpoint(), false, false),
                str("knowledge.minio.access_key", "MinIO Access Key", "", true, false),
                str("knowledge.minio.secret_key", "MinIO Secret Key", "", true, false),
                str("knowledge.minio.bucket_documents", "MinIO 文档 Bucket", knowledgeMinioProperties.getBucketDocuments(), false, false)
        );
    }

    @Override
    public Map<String, Object> defaults() {
        Map<String, Object> map = new LinkedHashMap<>();
        for (SettingFieldSchema field : schema()) {
            map.put(field.getKey(), field.getDefaultValue());
        }
        return map;
    }

    @Override
    public void onChanged() {
        integrationClientCache.invalidate();
    }

    @Override
    public void validate(Map<String, Object> items) {
        for (Map.Entry<String, Object> entry : items.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value);
            if (isPathKey(key)) {
                validatePath(key, text);
            }
            if (key.endsWith(".base_url") || key.endsWith(".endpoint") || "upload.base_url".equals(key)
                    || "app.deploy.host".equals(key) || "tts.base_url".equals(key)) {
                if (StrUtil.isNotBlank(text) && text.contains("..")) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, key + " 非法");
                }
            }
        }
    }

    private void validatePath(String key, String path) {
        if (StrUtil.isBlank(path)) {
            return;
        }
        if (path.contains("..")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, key + " 不允许包含 ..");
        }
    }

    private boolean isPathKey(String key) {
        return key.endsWith("_dir") || key.endsWith("_path") || "upload.path".equals(key)
                || key.contains("upload_dir") || key.contains("code_output") || key.contains("code_deploy")
                || key.contains("ref_audio");
    }

    private SettingFieldSchema str(String key, String label, String defaultValue, boolean sensitive, boolean danger) {
        return SettingFieldSchema.builder()
                .key(key)
                .valueType(SiteSettingConstant.TYPE_STRING)
                .label(label)
                .description(sensitive ? "敏感字段：留空表示不修改" : label)
                .defaultValue(nullToEmpty(defaultValue))
                .sensitive(sensitive)
                .danger(danger)
                .build();
    }

    private SettingFieldSchema path(String key, String label, String defaultValue) {
        return SettingFieldSchema.builder()
                .key(key)
                .valueType(SiteSettingConstant.TYPE_STRING)
                .label(label)
                .description("路径变更后静态资源映射可能需重启生效")
                .defaultValue(nullToEmpty(defaultValue))
                .sensitive(false)
                .danger(true)
                .build();
    }

    private SettingFieldSchema bool(String key, String label, boolean defaultValue) {
        return SettingFieldSchema.builder()
                .key(key)
                .valueType(SiteSettingConstant.TYPE_BOOL)
                .label(label)
                .description(label)
                .defaultValue(defaultValue)
                .build();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
