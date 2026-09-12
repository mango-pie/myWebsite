package com.ai.agent.config;

import cn.hutool.core.util.StrUtil;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

import java.time.Duration;

@Configuration
@org.springframework.boot.context.properties.EnableConfigurationProperties(AgentChatModelConfig.AgentChatModelProperties.class)
public class AgentChatModelConfig {

    @Bean(name = "agentChatModel")
    @ConditionalOnProperty(prefix = "chat.agent", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ChatModel agentChatModel(AgentChatModelProperties properties) {
        return OpenAiChatModel.builder()
                .baseUrl(properties.getBaseUrl())
                .apiKey(StrUtil.blankToDefault(properties.getApiKey(), "missing-key"))
                .modelName(properties.getModelName())
                .timeout(Duration.ofSeconds(Math.max(10, properties.getTimeoutSeconds())))
                .temperature(properties.getTemperature())
                .maxTokens(properties.getMaxTokens())
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    @Data
    @ConfigurationProperties(prefix = "langchain4j.open-ai.agent-chat-model")
    public static class AgentChatModelProperties {
        private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        private String apiKey;
        private String modelName = "qwen-plus";
        private int maxTokens = 4096;
        private double temperature = 0.3;
        private int timeoutSeconds = 60;
    }
}
