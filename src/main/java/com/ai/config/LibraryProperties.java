package com.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 资料库模块配置：MinIO bucket、配额与回收站保留期。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.library")
public class LibraryProperties {

    private String bucket = "library-files";
    private long quotaBytes = 5L * 1024 * 1024 * 1024;
    private long maxFileSizeBytes = 2L * 1024 * 1024 * 1024;
    private int presignExpireSeconds = 3600;
    private int recycleRetentionDays = 30;
}
