package com.ai.service.knowledge;

import com.ai.model.dto.knowledge.KnowledgeIngestBatchUrlRequest;
import com.ai.model.vo.knowledge.KnowledgeIngestBatchUrlVO;

public interface KnowledgeMergeDistillService {

    /**
     * 多 URL 抓取 → 分源短摘要 → 合并蒸馏 → 仅产出 1 条 note。
     */
    KnowledgeIngestBatchUrlVO mergeDistill(KnowledgeIngestBatchUrlRequest request, Long userId);
}
