package com.ai.job;

import com.ai.config.ConditionalOnModule;

import com.ai.constant.knowledge.KnowledgeReadingJobConstant;
import com.ai.exception.BusinessException;
import com.ai.model.dto.knowledge.KnowledgeIngestBatchUrlRequest;
import com.ai.model.entity.knowledge.KnowledgeReadingJob;
import com.ai.model.vo.knowledge.KnowledgeIngestBatchUrlVO;
import com.ai.service.knowledge.KnowledgeMergeDistillService;
import com.ai.service.knowledge.KnowledgeReadingJobService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 精读合蒸任务 Worker：全局串行消费 PENDING 任务。
 */
@Slf4j
@ConditionalOnModule("reading")
@Component
public class KnowledgeReadingJobWorker {

    @Resource
    private KnowledgeReadingJobService knowledgeReadingJobService;

    @Resource
    private KnowledgeMergeDistillService knowledgeMergeDistillService;

    @Scheduled(fixedDelay = 3000)
    public void pollAndRun() {
        try {
            knowledgeReadingJobService.failStuckRunning(KnowledgeReadingJobConstant.STUCK_TIMEOUT_MINUTES);
            KnowledgeReadingJob job = knowledgeReadingJobService.claimNext();
            if (job == null) {
                return;
            }
            runJob(job);
        } catch (Exception e) {
            log.error("Knowledge reading job worker failed", e);
        }
    }

    private void runJob(KnowledgeReadingJob job) {
        Long jobId = job.getId();
        try {
            KnowledgeIngestBatchUrlRequest request = knowledgeReadingJobService.parseRequest(job);
            KnowledgeIngestBatchUrlVO result = knowledgeMergeDistillService.mergeDistill(
                    request,
                    job.getUserId(),
                    progress -> knowledgeReadingJobService.markProgress(jobId, progress)
            );
            knowledgeReadingJobService.markSuccess(jobId, result);
            log.info("Knowledge reading job success, jobId={}, noteId={}", jobId, result.getNoteId());
        } catch (BusinessException e) {
            knowledgeReadingJobService.markFailed(jobId, e.getMessage());
            log.warn("Knowledge reading job business failed, jobId={}, msg={}", jobId, e.getMessage());
        } catch (Exception e) {
            knowledgeReadingJobService.markFailed(jobId,
                    e.getMessage() == null ? "精读任务执行异常" : e.getMessage());
            log.error("Knowledge reading job unexpected failed, jobId={}", jobId, e);
        }
    }
}
