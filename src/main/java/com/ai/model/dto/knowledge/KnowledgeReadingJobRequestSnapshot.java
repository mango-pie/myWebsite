package com.ai.model.dto.knowledge;

import lombok.Data;

/**
 * 写入 knowledge_reading_job.request_json 的快照，区分搜索 / 合蒸阶段。
 */
@Data
public class KnowledgeReadingJobRequestSnapshot {

    public static final String PHASE_SEARCH = "SEARCH";
    public static final String PHASE_DISTILL = "DISTILL";

    private String phase;
    private KnowledgeSearchPreviewRequest search;
    private KnowledgeIngestBatchUrlRequest distill;
}
