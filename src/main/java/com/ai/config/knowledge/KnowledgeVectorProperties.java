package com.ai.config.knowledge;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "knowledge.vector.datasource")
public class KnowledgeVectorProperties {

    private String url = "jdbc:postgresql://localhost:5432/knowledge_ai";

    private String username = "kai";

    private String password = "kai123";
}
