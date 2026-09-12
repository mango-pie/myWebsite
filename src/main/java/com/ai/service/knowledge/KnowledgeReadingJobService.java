package com.ai.service.knowledge;

import com.ai.model.dto.knowledge.KnowledgeIngestBatchUrlRequest;
import com.ai.model.dto.knowledge.KnowledgeReadingJobQueryRequest;
import com.ai.model.dto.knowledge.KnowledgeReadingJobRequestSnapshot;
import com.ai.model.dto.knowledge.KnowledgeSearchPreviewRequest;
import com.ai.model.entity.knowledge.KnowledgeReadingJob;
import com.ai.model.vo.knowledge.KnowledgeIngestBatchUrlVO;
import com.ai.model.vo.knowledge.KnowledgeReadingJobVO;
import com.mybatisflex.core.paginate.Page;

public interface KnowledgeReadingJobService {

    /** 异步搜索：立即返回 jobId，progress=SEARCHING */
    KnowledgeReadingJobVO submitSearch(KnowledgeSearchPreviewRequest request, Long userId);

    /** 合蒸入队：无 jobId 新建；有 jobId 则复用 AWAITING_SELECT 任务 */
    KnowledgeReadingJobVO submit(KnowledgeIngestBatchUrlRequest request, Long userId);

    KnowledgeReadingJobVO getJob(Long jobId, Long userId);

    Page<KnowledgeReadingJobVO> page(KnowledgeReadingJobQueryRequest request, Long userId);

    KnowledgeReadingJobVO runSync(KnowledgeIngestBatchUrlRequest request, Long userId);

    /** 抢占下一条可执行任务（SEARCHING 或 QUEUED），不含 AWAITING_SELECT */
    KnowledgeReadingJob claimNext();

    KnowledgeReadingJobRequestSnapshot parseSnapshot(KnowledgeReadingJob job);

    KnowledgeIngestBatchUrlRequest parseRequest(KnowledgeReadingJob job);

    void markProgress(Long jobId, String progress);

    void markAwaitingSelect(Long jobId, com.ai.model.vo.knowledge.KnowledgeSearchPreviewVO preview);

    /** 大纲确认步：合蒸大纲已生成，等人确认（WAITING / AWAITING_OUTLINE） */
    void markAwaitingOutline(Long jobId, String outline);

    /** 大纲确认：写入确认后的大纲并重新入队（PENDING / QUEUED） */
    KnowledgeReadingJobVO confirmOutline(Long jobId, String outline, Long userId);

    /** 失败任务重试：仅重读失败来源（failedSources），不超过 MAX_RETRY 次 */
    KnowledgeReadingJobVO retry(Long jobId, Long userId);

    void markSuccess(Long jobId, KnowledgeIngestBatchUrlVO result);

    void markFailed(Long jobId, String errorMsg);

    int failStuckRunning(int timeoutMinutes);

    /** QUEUED 排队超时判定：超时标记失败并给出「排队超时，请重试」提示 */
    int failStuckQueued(int timeoutMinutes);

    /** Worker：执行已 claim 的搜索阶段 */
    void executeSearch(KnowledgeReadingJob job);

    /** Worker：执行已 claim 的合蒸阶段 */
    void executeDistill(KnowledgeReadingJob job);
}
