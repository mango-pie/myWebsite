package com.ai.config;

import cn.hutool.core.util.StrUtil;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ChatImageCaptionConfig {

    @Bean(name = "imageCaptionChatModel")
    @ConditionalOnProperty(prefix = "chat.image-caption", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ChatModel imageCaptionChatModel(ChatImageCaptionProperties properties) {
        return OpenAiChatModel.builder()
                .baseUrl(properties.getBaseUrl())
                .apiKey(StrUtil.blankToDefault(properties.getApiKey(), "missing-key"))
                .modelName(properties.getModelName())
                .timeout(Duration.ofSeconds(Math.max(5, properties.getTimeoutSeconds())))
                .temperature(0.2)
                .maxTokens(1024)
                .build();
    }
}
