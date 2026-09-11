package com.ai.config.knowledge;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "knowledge.jina")
public class KnowledgeJinaProperties {

    private String baseUrl = "https://r.jina.ai/";

    private int timeoutSeconds = 30;
}
