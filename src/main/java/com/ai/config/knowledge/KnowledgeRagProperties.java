package com.ai.config.knowledge;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "knowledge.rag")
public class KnowledgeRagProperties {

    private int topK = 5;

    private int chunkSize = 1200;

    private int chunkOverlap = 150;
}
