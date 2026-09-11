package com.ai.config.knowledge;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "knowledge.minio")
public class KnowledgeMinioProperties {

    private boolean enabled = true;

    private String endpoint = "http://localhost:9000";

    private String accessKey = "minioadmin";

    private String secretKey = "minioadmin";

    private String bucketDocuments = "knowledge-documents";

    private int presignExpireSeconds = 3600;
}
