package com.ai.service.knowledge.impl;

import com.ai.constant.knowledge.KnowledgeReadingJobConstant;
import com.ai.exception.BusinessException;
import com.ai.mapper.knowledge.KnowledgeReadingJobMapper;
import com.ai.model.dto.knowledge.KnowledgeIngestBatchUrlRequest;
import com.ai.model.dto.knowledge.KnowledgeReadingJobRequestSnapshot;
import com.ai.model.entity.knowledge.KnowledgeReadingJob;
import com.ai.model.vo.knowledge.KnowledgeIngestBatchUrlVO;
import com.ai.model.vo.knowledge.KnowledgeReadingJobVO;
import com.ai.service.knowledge.KnowledgeAiModelService;
import com.ai.service.knowledge.KnowledgeMergeDistillService;
import com.ai.service.knowledge.support.DeepSeekBalanceGuard;
import com.ai.service.knowledge.support.ReadingJobNotifier;
import com.ai.service.knowledge.support.SourceDuplicateChecker;
import com.ai.setting.runtime.ReadingRuntimeSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验收脚本 G/H：失败任务差量重试（保留已勾选 URL、只重读失败来源、上限 1 次）。
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeReadingJobServiceImplTest {

    @Mock
    private KnowledgeReadingJobMapper knowledgeReadingJobMapper;
    @Mock
    private KnowledgeMergeDistillService knowledgeMergeDistillService;
    @Mock
    private ReadingRuntimeSettings readingRuntimeSettings;
    @Mock
    private KnowledgeAiModelService aiModelService;
    @Mock
    private ReadingJobNotifier readingJobNotifier;
    @Mock
    private SourceDuplicateChecker sourceDuplicateChecker;
    @Mock
    private DeepSeekBalanceGuard deepSeekBalanceGuard;

    private ObjectMapper objectMapper;
    private KnowledgeReadingJobServiceImpl service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new KnowledgeReadingJobServiceImpl();
        ReflectionTestUtils.setField(service, "knowledgeReadingJobMapper", knowledgeReadingJobMapper);
        ReflectionTestUtils.setField(service, "knowledgeMergeDistillService", knowledgeMergeDistillService);
        ReflectionTestUtils.setField(service, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(service, "readingRuntimeSettings", readingRuntimeSettings);
        ReflectionTestUtils.setField(service, "knowledgeSearchStrategies", List.of());
        ReflectionTestUtils.setField(service, "aiModelService", aiModelService);
        ReflectionTestUtils.setField(service, "readingJobNotifier", readingJobNotifier);
        ReflectionTestUtils.setField(service, "sourceDuplicateChecker", sourceDuplicateChecker);
        ReflectionTestUtils.setField(service, "deepSeekBalanceGuard", deepSeekBalanceGuard);
        lenient().when(readingRuntimeSettings.maxQueuedJobs()).thenReturn(5);
        lenient().when(knowledgeReadingJobMapper.selectCountByQuery(any())).thenReturn(0L);
    }

    private KnowledgeReadingJob failedJob(String resultJson) throws Exception {
        KnowledgeReadingJob job = new KnowledgeReadingJob();
        job.setId(1L);
        job.setUserId(1L);
        job.setStatus(KnowledgeReadingJobConstant.STATUS_FAILED);
        job.setRetryCount(0);
        KnowledgeIngestBatchUrlRequest distill = new KnowledgeIngestBatchUrlRequest();
        distill.setUrls(List.of("https://a.com", "https://b.com", "https://c.com"));
        distill.setAgentQuery("Java 集合");
        KnowledgeReadingJobRequestSnapshot snapshot = new KnowledgeReadingJobRequestSnapshot();
        snapshot.setPhase(KnowledgeReadingJobRequestSnapshot.PHASE_DISTILL);
        snapshot.setDistill(distill);
        job.setRequestJson(objectMapper.writeValueAsString(snapshot));
        job.setResultJson(resultJson);
        return job;
    }

    /** 脚本 G：FAILED → retry，仅重读失败来源，保留 agentQuery 与门闩上下文 */
    @Test
    void retry_rebuildsOnlyFailedSources() throws Exception {
        KnowledgeIngestBatchUrlVO result = new KnowledgeIngestBatchUrlVO();
        result.setUsedCount(2);
        result.setFailCount(1);
        KnowledgeIngestBatchUrlVO.FailedSource f = new KnowledgeIngestBatchUrlVO.FailedSource();
        f.setUrl("https://b.com");
        f.setReasonCode("FAILED");
        f.setErrorMessage("AI 读取正文为空");
        result.getFailedSources().add(f);
        KnowledgeReadingJob job = failedJob(objectMapper.writeValueAsString(result));
        when(knowledgeReadingJobMapper.selectOneById(1L)).thenReturn(job);

        KnowledgeReadingJobVO vo = service.retry(1L, 1L);

        assertEquals(KnowledgeReadingJobConstant.STATUS_PENDING, vo.getStatus());
        assertEquals(1, vo.getRetryCount());
        assertEquals(1, vo.getTotal());
        ArgumentCaptor<KnowledgeReadingJob> captor = ArgumentCaptor.forClass(KnowledgeReadingJob.class);
        verify(knowledgeReadingJobMapper).update(captor.capture());
        KnowledgeReadingJob updated = captor.getValue();
        assertEquals(KnowledgeReadingJobConstant.STATUS_PENDING, updated.getStatus());
        assertEquals(KnowledgeReadingJobConstant.PROGRESS_QUEUED, updated.getProgress());
        assertEquals(1, updated.getRetryCount());
        assertNull(updated.getErrorMsg());
        assertNull(updated.getResultJson());
        KnowledgeReadingJobRequestSnapshot snapshot =
                objectMapper.readValue(updated.getRequestJson(), KnowledgeReadingJobRequestSnapshot.class);
        assertEquals(List.of("https://b.com"), snapshot.getDistill().getUrls());
        assertEquals("Java 集合", snapshot.getDistill().getAgentQuery());
    }

    /** 脚本 H 补充：无失败来源（全量失败无 partial 结果）时回退重跑原 URL 全集 */
    @Test
    void retry_fallsBackToAllUrlsWhenNoFailedSources() throws Exception {
        KnowledgeReadingJob job = failedJob(null);
        when(knowledgeReadingJobMapper.selectOneById(1L)).thenReturn(job);

        KnowledgeReadingJobVO vo = service.retry(1L, 1L);

        assertEquals(3, vo.getTotal());
        ArgumentCaptor<KnowledgeReadingJob> captor = ArgumentCaptor.forClass(KnowledgeReadingJob.class);
        verify(knowledgeReadingJobMapper).update(captor.capture());
        KnowledgeReadingJobRequestSnapshot snapshot =
                objectMapper.readValue(captor.getValue().getRequestJson(), KnowledgeReadingJobRequestSnapshot.class);
        assertEquals(List.of("https://a.com", "https://b.com", "https://c.com"), snapshot.getDistill().getUrls());
    }

    /** 脚本 G：已达重试上限（MAX_RETRY=1）时拒绝再次重试 */
    @Test
    void retry_rejectsWhenExhausted() throws Exception {
        KnowledgeReadingJob job = failedJob(null);
        job.setRetryCount(KnowledgeReadingJobConstant.MAX_RETRY);
        when(knowledgeReadingJobMapper.selectOneById(1L)).thenReturn(job);

        assertThrows(BusinessException.class, () -> service.retry(1L, 1L));
        verify(knowledgeReadingJobMapper, never()).update(any());
    }

    /** 脚本 G：非失败任务不可重试 */
    @Test
    void retry_rejectsNonFailedJob() throws Exception {
        KnowledgeReadingJob job = failedJob(null);
        job.setStatus(KnowledgeReadingJobConstant.STATUS_SUCCESS);
        when(knowledgeReadingJobMapper.selectOneById(1L)).thenReturn(job);

        assertThrows(BusinessException.class, () -> service.retry(1L, 1L));
        verify(knowledgeReadingJobMapper, never()).update(any());
    }

    /** 脚本 G：排队任务达上限时重试被熔断（防失败风暴） */
    @Test
    void retry_rejectsWhenQueueFull() throws Exception {
        KnowledgeReadingJob job = failedJob(null);
        when(knowledgeReadingJobMapper.selectOneById(1L)).thenReturn(job);
        when(knowledgeReadingJobMapper.selectCountByQuery(any())).thenReturn(5L);

        BusinessException e = assertThrows(BusinessException.class, () -> service.retry(1L, 1L));
        assertTrue(e.getMessage().contains("排队任务已达上限"));
        verify(knowledgeReadingJobMapper, never()).update(any());
    }
}
