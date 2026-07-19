package com.ai.setting;

import cn.hutool.core.util.StrUtil;
import com.ai.config.ChatImageCaptionProperties;
import com.ai.service.IntegrationCredentialsService;
import com.ai.setting.runtime.ChatRuntimeSettings;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 按当前 integration 凭证构建 Langchain4j OpenAI 兼容模型。
 */
@Component
public class IntegrationOpenAiModelFactory {

    @Resource
    private IntegrationCredentialsService credentials;

    @Resource
    private ObjectProvider<ChatRuntimeSettings> chatRuntimeSettingsProvider;

    @Resource
    private ChatImageCaptionProperties chatImageCaptionProperties;

    @Value("${langchain4j.open-ai.agent-chat-model.model-name:qwen-plus}")
    private String agentModelName;
    @Value("${langchain4j.open-ai.agent-chat-model.max-tokens:4096}")
    private int agentMaxTokens;
    @Value("${langchain4j.open-ai.agent-chat-model.temperature:0.3}")
    private double agentTemperature;
    @Value("${langchain4j.open-ai.agent-chat-model.timeout-seconds:60}")
    private int agentTimeoutSeconds;

    @Value("${langchain4j.open-ai.streaming-chat-model.model-name:deepseek-v4-flash}")
    private String codegenModelName;
    @Value("${langchain4j.open-ai.streaming-chat-model.max-tokens:8192}")
    private int codegenMaxTokens;

    public ChatModel agentChatModel() {
        return OpenAiChatModel.builder()
                .baseUrl(trim(credentials.agentBaseUrl()))
                .apiKey(StrUtil.blankToDefault(credentials.agentApiKey(), "missing-key"))
                .modelName(agentModelName)
                .timeout(Duration.ofSeconds(Math.max(10, agentTimeoutSeconds)))
                .temperature(agentTemperature)
                .maxTokens(agentMaxTokens)
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    public ChatModel imageCaptionChatModel() {
        ChatRuntimeSettings chatRuntimeSettings = chatRuntimeSettingsProvider.getIfAvailable();
        String modelName = chatRuntimeSettings != null
                ? chatRuntimeSettings.imageCaptionModelName()
                : chatImageCaptionProperties.getModelName();
        int timeoutSeconds = chatRuntimeSettings != null
                ? chatRuntimeSettings.imageCaptionTimeoutSeconds()
                : chatImageCaptionProperties.getTimeoutSeconds();
        return OpenAiChatModel.builder()
                .baseUrl(trim(credentials.imageCaptionBaseUrl()))
                .apiKey(StrUtil.blankToDefault(credentials.imageCaptionApiKey(), "missing-key"))
                .modelName(modelName)
                .timeout(Duration.ofSeconds(Math.max(10, timeoutSeconds)))
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    public StreamingChatModel codegenStreamingChatModel() {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(trim(credentials.codegenBaseUrl()))
                .apiKey(StrUtil.blankToDefault(credentials.codegenApiKey(), "missing-key"))
                .modelName(codegenModelName)
                .maxTokens(codegenMaxTokens)
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    private String trim(String baseUrl) {
        String value = baseUrl == null ? "" : baseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
