package com.ai.service.knowledge;

import com.ai.model.dto.knowledge.KnowledgeIngestBatchUrlRequest;
import com.ai.model.entity.knowledge.KnowledgeReadingJob;
import com.ai.model.vo.knowledge.KnowledgeIngestBatchUrlVO;
import com.ai.model.vo.knowledge.KnowledgeReadingJobVO;

public interface KnowledgeReadingJobService {

    KnowledgeReadingJobVO submit(KnowledgeIngestBatchUrlRequest request, Long userId);

    KnowledgeReadingJobVO getJob(Long jobId, Long userId);

    KnowledgeReadingJobVO runSync(KnowledgeIngestBatchUrlRequest request, Long userId);

    KnowledgeReadingJob claimNext();

    KnowledgeIngestBatchUrlRequest parseRequest(KnowledgeReadingJob job);

    void markProgress(Long jobId, String progress);

    void markSuccess(Long jobId, KnowledgeIngestBatchUrlVO result);

    void markFailed(Long jobId, String errorMsg);

    int failStuckRunning(int timeoutMinutes);
}
