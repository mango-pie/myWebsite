package com.ai.model.dto.knowledge;

import lombok.Data;

@Data
public class KnowledgeIngestUrlRequest {

    private String url;
    private String title;
    private String tags;
    /** URL or AGENT */
    private String sourceType = "URL";
    /** AGENT 场景下的学习目标/搜索词，写入 note tags 便于溯源 */
    private String agentQuery;
}
