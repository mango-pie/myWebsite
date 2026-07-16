package com.ai.model.dto.setting;

import lombok.Data;

@Data
public class IntegrationTestRequest {
    /**
     * knowledge_ai | jina | astrbot | tts | codegen | minio | agent
     */
    private String target;
}
