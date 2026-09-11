package com.ai.config.knowledge;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "knowledge.ai")
public class KnowledgeAiProperties {

    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    private String apiKey;

    private String chatModel = "deepseek-v3";

    private String embeddingModel = "text-embedding-v2";

    private Integer embeddingDimension = 1536;

    private Double temperature = 0.2;

    private Integer maxTokens = 2048;

    private Integer timeoutSeconds = 120;
}
