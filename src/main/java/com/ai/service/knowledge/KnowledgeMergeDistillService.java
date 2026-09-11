package com.ai.service.knowledge;

import com.ai.model.dto.knowledge.KnowledgeIngestBatchUrlRequest;
import com.ai.model.vo.knowledge.KnowledgeIngestBatchUrlVO;

import java.util.function.Consumer;

public interface KnowledgeMergeDistillService {

    /**
     * 多 URL AI 读页物化 → 一次精读重构 → 仅产出 1 条 note。
     */
    KnowledgeIngestBatchUrlVO mergeDistill(KnowledgeIngestBatchUrlRequest request, Long userId);

    /**
     * @param progressCallback 可选进度回调，值为 READING / DISTILLING
     */
    KnowledgeIngestBatchUrlVO mergeDistill(KnowledgeIngestBatchUrlRequest request,
                                           Long userId,
                                           Consumer<String> progressCallback);
}
