package com.ai.model.dto.knowledge;

import lombok.Data;

import java.util.List;

@Data
public class KnowledgeIngestBatchUrlRequest {

    private List<String> urls;
    /** 默认 AGENT */
    private String sourceType = "AGENT";
    private String agentQuery;
    /** 本次合蒸覆盖 Prompt；为空时使用 reading.distill.system_prompt */
    private String distillPrompt;
    private String tags;
}
