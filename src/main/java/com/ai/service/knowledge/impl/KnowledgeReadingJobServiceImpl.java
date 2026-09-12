package com.ai.service.knowledge.impl;

import com.ai.config.ConditionalOnModule;

import cn.hutool.core.util.StrUtil;
import com.ai.constant.AiUsageSceneConstant;
import com.ai.constant.knowledge.KnowledgeReadingJobConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.mapper.knowledge.KnowledgeReadingJobMapper;
import com.ai.model.dto.knowledge.KnowledgeIngestBatchUrlRequest;
import com.ai.model.dto.knowledge.KnowledgeReadingJobQueryRequest;
import com.ai.model.dto.knowledge.KnowledgeReadingJobRequestSnapshot;
import com.ai.model.dto.knowledge.KnowledgeSearchPreviewRequest;
import com.ai.model.dto.knowledge.SearchOptions;
import com.ai.model.entity.knowledge.KnowledgeReadingJob;
import com.ai.model.vo.knowledge.KnowledgeIngestBatchUrlVO;
import com.ai.model.vo.knowledge.KnowledgeReadingJobVO;
import com.ai.model.vo.knowledge.KnowledgeSearchPreviewVO;
import com.ai.model.vo.knowledge.SearchResult;
import com.ai.ops.AiUsageCallContext;
import com.ai.service.knowledge.KnowledgeAiModelService;
import com.ai.service.knowledge.KnowledgeMergeDistillService;
import com.ai.service.knowledge.KnowledgeReadingJobService;
import com.ai.service.knowledge.KnowledgeSearchStrategy;
import com.ai.service.knowledge.support.DeepSeekBalanceGuard;
import com.ai.service.knowledge.support.ReadingJobNotifier;
import com.ai.service.knowledge.support.SourceDuplicateChecker;
import com.ai.setting.runtime.ReadingRuntimeSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@ConditionalOnModule("knowledge")
@Service
public class KnowledgeReadingJobServiceImpl implements KnowledgeReadingJobService {

    @Resource
    private KnowledgeReadingJobMapper knowledgeReadingJobMapper;

    @Resource
    private KnowledgeMergeDistillService knowledgeMergeDistillService;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private ReadingRuntimeSettings readingRuntimeSettings;

    @Resource
    private java.util.List<KnowledgeSearchStrategy> knowledgeSearchStrategies;

    @Resource
    private KnowledgeAiModelService aiModelService;

    @Resource
    private ReadingJobNotifier readingJobNotifier;

    @Resource
    private SourceDuplicateChecker sourceDuplicateChecker;

    @Resource
    private DeepSeekBalanceGuard deepSeekBalanceGuard;

    @Override
    public KnowledgeReadingJobVO submitSearch(KnowledgeSearchPreviewRequest request, Long userId) {
        if (request == null || StrUtil.isBlank(request.getGoal())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "学习目标 goal 不能为空");
        }
        // 余额检查是外部 HTTP 调用，不持数据库连接；本方法只有一条 insert，单语句自提交
        if (readingRuntimeSettings.balanceCheckEnabled()) {
            deepSeekBalanceGuard.assertCanIngest();
        }
        KnowledgeSearchPreviewRequest search = new KnowledgeSearchPreviewRequest();
        search.setGoal(request.getGoal().trim());
        search.setPreference(request.getPreference());
        search.setDomainId(request.getDomainId());
        search.setGatePassId(request.getGatePassId());
        search.setBranchId(request.getBranchId());
        search.setSkipGate(request.getSkipGate());

        assertQueueCapacity(userId);

        KnowledgeReadingJobRequestSnapshot snapshot = new KnowledgeReadingJobRequestSnapshot();
        snapshot.setPhase(KnowledgeReadingJobRequestSnapshot.PHASE_SEARCH);
        snapshot.setSearch(search);

        LocalDateTime now = LocalDateTime.now();
        KnowledgeReadingJob job = new KnowledgeReadingJob();
        job.setUserId(userId);
        job.setStatus(KnowledgeReadingJobConstant.STATUS_PENDING);
        job.setProgress(KnowledgeReadingJobConstant.PROGRESS_SEARCHING);
        job.setRequestJson(writeJson(snapshot));
        job.setCreateTime(now);
        job.setUpdateTime(now);
        job.setIsDelete(0);
        knowledgeReadingJobMapper.insert(job);

        KnowledgeReadingJobVO vo = toPendingVo(job);
        vo.setGoal(search.getGoal());
        vo.setTitle(search.getGoal());
        return vo;
    }

    @Override
    public KnowledgeReadingJobVO submit(KnowledgeIngestBatchUrlRequest request, Long userId) {
        List<String> urls = normalizeUrls(request);
        if (request.getJobId() != null) {
            return continueFromSearch(request, urls, userId);
        }
        if (readingRuntimeSettings.balanceCheckEnabled()) {
            deepSeekBalanceGuard.assertCanIngest();
        }
        assertQueueCapacity(userId);
        KnowledgeIngestBatchUrlRequest distill = copyDistillRequest(request, urls);
        KnowledgeReadingJobRequestSnapshot snapshot = new KnowledgeReadingJobRequestSnapshot();
        snapshot.setPhase(KnowledgeReadingJobRequestSnapshot.PHASE_DISTILL);
        snapshot.setDistill(distill);

        // 大纲确认步：AI 生成大纲是外部调用，必须在落库前完成，
        // 避免「先插 PENDING 再异步改 WAITING」让 Worker 抢到尚未就绪的任务
        String pendingOutline = resolvePendingOutline(distill);

        LocalDateTime now = LocalDateTime.now();
        KnowledgeReadingJob job = new KnowledgeReadingJob();
        job.setUserId(userId);
        applyAwaitingOutline(job, pendingOutline, snapshot);
        if (pendingOutline == null) {
            job.setStatus(KnowledgeReadingJobConstant.STATUS_PENDING);
            job.setProgress(KnowledgeReadingJobConstant.PROGRESS_QUEUED);
        }
        job.setRequestJson(writeJson(snapshot));
        job.setRetryCount(0);
        job.setCreateTime(now);
        job.setUpdateTime(now);
        job.setIsDelete(0);
        knowledgeReadingJobMapper.insert(job);

        KnowledgeReadingJobVO vo = toPendingVo(job);
        vo.setTotal(urls.size());
        if (StrUtil.isNotBlank(distill.getAgentQuery())) {
            vo.setTitle(distill.getAgentQuery().trim());
        }
        if (pendingOutline != null) {
            readingJobNotifier.notify(job.getUserId(), job.getId(),
                    KnowledgeReadingJobConstant.STATUS_WAITING, KnowledgeReadingJobConstant.PROGRESS_AWAITING_OUTLINE);
            vo.setStatus(KnowledgeReadingJobConstant.STATUS_WAITING);
            vo.setProgress(KnowledgeReadingJobConstant.PROGRESS_AWAITING_OUTLINE);
            vo.setPendingOutline(pendingOutline);
            vo.setSuccess(false);
        }
        return vo;
    }

    private String resolvePendingOutline(KnowledgeIngestBatchUrlRequest distill) {
        if (!Boolean.TRUE.equals(distill.getOutlineConfirm())) {
            return null;
        }
        String goal = StrUtil.blankToDefault(distill.getAgentQuery(),
                distill.getUrls() == null ? "" : "多来源合蒸");
        return buildOutline(goal, null);
    }

    private void applyAwaitingOutline(KnowledgeReadingJob job, String pendingOutline,
                                      KnowledgeReadingJobRequestSnapshot snapshot) {
        if (pendingOutline == null) {
            return;
        }
        job.setStatus(KnowledgeReadingJobConstant.STATUS_WAITING);
        job.setProgress(KnowledgeReadingJobConstant.PROGRESS_AWAITING_OUTLINE);
        job.setResultJson(writeJson(StrUtil.blankToDefault(pendingOutline, "")));
        job.setErrorMsg(null);
        job.setStartedAt(null);
        job.setFinishedAt(null);
    }

    private KnowledgeReadingJobVO continueFromSearch(
            KnowledgeIngestBatchUrlRequest request, List<String> urls, Long userId) {
        KnowledgeReadingJob job = requireJob(request.getJobId(), userId);
        if (!KnowledgeReadingJobConstant.STATUS_WAITING.equals(job.getStatus())
                || !KnowledgeReadingJobConstant.PROGRESS_AWAITING_SELECT.equals(job.getProgress())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "当前任务不在「待勾选」状态，无法提交合蒸");
        }
        KnowledgeReadingJobRequestSnapshot prev = parseSnapshot(job);
        KnowledgeIngestBatchUrlRequest distill = copyDistillRequest(request, urls);
        if (StrUtil.isBlank(distill.getAgentQuery()) && prev.getSearch() != null) {
            distill.setAgentQuery(prev.getSearch().getGoal());
        }
        KnowledgeReadingJobRequestSnapshot snapshot = new KnowledgeReadingJobRequestSnapshot();
        snapshot.setPhase(KnowledgeReadingJobRequestSnapshot.PHASE_DISTILL);
        snapshot.setSearch(prev.getSearch());
        snapshot.setDistill(distill);

        // 大纲确认步：AI 生成大纲在落库前完成，避免 Worker 抢到中间态任务
        String pendingOutline = resolvePendingOutline(distill);

        LocalDateTime now = LocalDateTime.now();
        if (pendingOutline != null) {
            applyAwaitingOutline(job, pendingOutline, snapshot);
        } else {
            job.setStatus(KnowledgeReadingJobConstant.STATUS_PENDING);
            job.setProgress(KnowledgeReadingJobConstant.PROGRESS_QUEUED);
            job.setResultJson(null);
            job.setErrorMsg(null);
            job.setStartedAt(null);
            job.setFinishedAt(null);
        }
        job.setRequestJson(writeJson(snapshot));
        job.setUpdateTime(now);
        knowledgeReadingJobMapper.update(job);

        KnowledgeReadingJobVO vo = toPendingVo(job);
        vo.setTotal(urls.size());
        if (StrUtil.isNotBlank(distill.getAgentQuery())) {
            vo.setTitle(distill.getAgentQuery().trim());
            vo.setGoal(distill.getAgentQuery().trim());
        }
        if (pendingOutline != null) {
            readingJobNotifier.notify(job.getUserId(), job.getId(),
                    KnowledgeReadingJobConstant.STATUS_WAITING, KnowledgeReadingJobConstant.PROGRESS_AWAITING_OUTLINE);
            vo.setPendingOutline(pendingOutline);
            vo.setSuccess(false);
        }
        return vo;
    }

    @Override
    public KnowledgeReadingJobVO getJob(Long jobId, Long userId) {
        return toVo(requireJob(jobId, userId));
    }

    @Override
    public Page<KnowledgeReadingJobVO> page(KnowledgeReadingJobQueryRequest request, Long userId) {
        int pageNum = request == null ? 1 : Math.max(1, request.getPageNum());
        int pageSize = request == null ? 20 : Math.min(100, Math.max(1, request.getPageSize()));
        String status = request == null ? null : StrUtil.trim(request.getStatus());
        boolean activeOnly = request != null && Boolean.TRUE.equals(request.getActiveOnly());

        QueryWrapper wrapper = QueryWrapper.create()
                .eq("user_id", userId)
                .eq("is_delete", 0)
                .orderBy("create_time", false);

        if (activeOnly) {
            wrapper.in("status", List.of(
                    KnowledgeReadingJobConstant.STATUS_PENDING,
                    KnowledgeReadingJobConstant.STATUS_RUNNING,
                    KnowledgeReadingJobConstant.STATUS_WAITING));
        } else if (StrUtil.isNotBlank(status)) {
            wrapper.eq("status", status.toUpperCase());
        }

        Page<KnowledgeReadingJob> page = knowledgeReadingJobMapper.paginate(Page.of(pageNum, pageSize), wrapper);
        Page<KnowledgeReadingJobVO> voPage = new Page<>(pageNum, pageSize, page.getTotalRow());
        voPage.setRecords(page.getRecords().stream().map(this::toVo).toList());
        return voPage;
    }

    @Override
    public KnowledgeReadingJobVO runSync(KnowledgeIngestBatchUrlRequest request, Long userId) {
        KnowledgeIngestBatchUrlVO result = knowledgeMergeDistillService.mergeDistill(request, userId);
        KnowledgeReadingJobVO vo = new KnowledgeReadingJobVO();
        vo.setJobId(null);
        vo.setStatus(KnowledgeReadingJobConstant.STATUS_SUCCESS);
        vo.setProgress(KnowledgeReadingJobConstant.PROGRESS_DONE);
        vo.setSuccess(true);
        fillDistillResult(vo, result);
        vo.setFinishedAt(LocalDateTime.now());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeReadingJob claimNext() {
        long running = knowledgeReadingJobMapper.selectCountByQuery(QueryWrapper.create()
                .eq("status", KnowledgeReadingJobConstant.STATUS_RUNNING)
                .eq("is_delete", 0));
        if (running > 0) {
            return null;
        }
        KnowledgeReadingJob pending = knowledgeReadingJobMapper.selectOneByQuery(QueryWrapper.create()
                .eq("status", KnowledgeReadingJobConstant.STATUS_PENDING)
                .in("progress", List.of(
                        KnowledgeReadingJobConstant.PROGRESS_SEARCHING,
                        KnowledgeReadingJobConstant.PROGRESS_QUEUED))
                .eq("is_delete", 0)
                .orderBy("create_time", true)
                .limit(1));
        if (pending == null) {
            return null;
        }
        boolean searchPhase = KnowledgeReadingJobConstant.PROGRESS_SEARCHING.equals(pending.getProgress());
        LocalDateTime now = LocalDateTime.now();
        KnowledgeReadingJob patch = new KnowledgeReadingJob();
        patch.setStatus(KnowledgeReadingJobConstant.STATUS_RUNNING);
        patch.setProgress(searchPhase
                ? KnowledgeReadingJobConstant.PROGRESS_SEARCHING
                : KnowledgeReadingJobConstant.PROGRESS_READING);
        patch.setStartedAt(now);
        patch.setUpdateTime(now);
        int claimed = knowledgeReadingJobMapper.updateByQuery(
                patch,
                QueryWrapper.create()
                        .eq("id", pending.getId())
                        .eq("status", KnowledgeReadingJobConstant.STATUS_PENDING)
                        .eq("is_delete", 0));
        if (claimed != 1) {
            return null;
        }
        pending.setStatus(KnowledgeReadingJobConstant.STATUS_RUNNING);
        pending.setProgress(patch.getProgress());
        pending.setStartedAt(now);
        pending.setUpdateTime(now);
        return pending;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markProgress(Long jobId, String progress) {
        KnowledgeReadingJob job = knowledgeReadingJobMapper.selectOneById(jobId);
        if (job == null || !KnowledgeReadingJobConstant.STATUS_RUNNING.equals(job.getStatus())) {
            return;
        }
        job.setProgress(progress);
        job.setUpdateTime(LocalDateTime.now());
        knowledgeReadingJobMapper.update(job);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAwaitingSelect(Long jobId, KnowledgeSearchPreviewVO preview) {
        KnowledgeReadingJob job = knowledgeReadingJobMapper.selectOneById(jobId);
        if (job == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        job.setStatus(KnowledgeReadingJobConstant.STATUS_WAITING);
        job.setProgress(KnowledgeReadingJobConstant.PROGRESS_AWAITING_SELECT);
        job.setResultJson(writeJson(preview));
        job.setErrorMsg(null);
        job.setStartedAt(null);
        job.setFinishedAt(null);
        job.setUpdateTime(now);
        knowledgeReadingJobMapper.update(job);
        readingJobNotifier.notify(job.getUserId(), jobId,
                KnowledgeReadingJobConstant.STATUS_WAITING, KnowledgeReadingJobConstant.PROGRESS_AWAITING_SELECT);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAwaitingOutline(Long jobId, String outline) {
        KnowledgeReadingJob job = knowledgeReadingJobMapper.selectOneById(jobId);
        if (job == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        job.setStatus(KnowledgeReadingJobConstant.STATUS_WAITING);
        job.setProgress(KnowledgeReadingJobConstant.PROGRESS_AWAITING_OUTLINE);
        job.setResultJson(writeJson(StrUtil.blankToDefault(outline, "")));
        job.setErrorMsg(null);
        job.setStartedAt(null);
        job.setFinishedAt(null);
        job.setUpdateTime(now);
        knowledgeReadingJobMapper.update(job);
        readingJobNotifier.notify(job.getUserId(), jobId,
                KnowledgeReadingJobConstant.STATUS_WAITING, KnowledgeReadingJobConstant.PROGRESS_AWAITING_OUTLINE);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeReadingJobVO confirmOutline(Long jobId, String outline, Long userId) {
        KnowledgeReadingJob job = requireJob(jobId, userId);
        if (!KnowledgeReadingJobConstant.STATUS_WAITING.equals(job.getStatus())
                || !KnowledgeReadingJobConstant.PROGRESS_AWAITING_OUTLINE.equals(job.getProgress())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "当前任务不在「待确认大纲」状态");
        }
        KnowledgeReadingJobRequestSnapshot snapshot = parseSnapshot(job);
        if (snapshot.getDistill() == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务缺少合蒸请求");
        }
        String finalOutline = StrUtil.blankToDefault(StrUtil.trim(outline),
                StrUtil.blankToDefault(StrUtil.trim(snapshot.getDistill().getOutline()),
                        StrUtil.blankToDefault(StrUtil.trim(job.getResultJson()), "")));
        snapshot.getDistill().setOutline(finalOutline);
        snapshot.getDistill().setOutlineConfirm(false);

        LocalDateTime now = LocalDateTime.now();
        job.setStatus(KnowledgeReadingJobConstant.STATUS_PENDING);
        job.setProgress(KnowledgeReadingJobConstant.PROGRESS_QUEUED);
        job.setRequestJson(writeJson(snapshot));
        job.setResultJson(null);
        job.setErrorMsg(null);
        job.setStartedAt(null);
        job.setFinishedAt(null);
        job.setUpdateTime(now);
        knowledgeReadingJobMapper.update(job);

        KnowledgeReadingJobVO vo = toVo(job);
        vo.setOutline(finalOutline);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeReadingJobVO retry(Long jobId, Long userId) {
        KnowledgeReadingJob job = requireJob(jobId, userId);
        if (!KnowledgeReadingJobConstant.STATUS_FAILED.equals(job.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "仅失败任务可重试");
        }
        int retryCount = job.getRetryCount() == null ? 0 : job.getRetryCount();
        if (retryCount >= KnowledgeReadingJobConstant.MAX_RETRY) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "已达重试上限（" + KnowledgeReadingJobConstant.MAX_RETRY + " 次），请检查原因后重新采集");
        }
        // 防失败风暴：同用户排队任务已满时不放行新重试，由单 RUNNING 槽串行消化
        assertQueueCapacity(userId);
        KnowledgeReadingJobRequestSnapshot snapshot = parseSnapshot(job);
        if (snapshot.getDistill() == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务缺少合蒸请求");
        }
        // 差量重试：只重读失败来源（failedSources），不整单重跑
        List<String> urls = failedSourceUrls(job);
        if (urls == null || urls.isEmpty()) {
            urls = snapshot.getDistill().getUrls();
        }
        if (urls == null || urls.isEmpty()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务无可用来源，无法重试");
        }
        snapshot.getDistill().setUrls(urls);
        snapshot.getDistill().setOutlineConfirm(false);

        LocalDateTime now = LocalDateTime.now();
        job.setStatus(KnowledgeReadingJobConstant.STATUS_PENDING);
        job.setProgress(KnowledgeReadingJobConstant.PROGRESS_QUEUED);
        job.setRequestJson(writeJson(snapshot));
        job.setResultJson(null);
        job.setErrorMsg(null);
        job.setRetryCount(retryCount + 1);
        job.setStartedAt(null);
        job.setFinishedAt(null);
        job.setUpdateTime(now);
        knowledgeReadingJobMapper.update(job);

        KnowledgeReadingJobVO vo = toPendingVo(job);
        vo.setTotal(urls.size());
        vo.setRetryCount(retryCount + 1);
        if (StrUtil.isNotBlank(snapshot.getDistill().getAgentQuery())) {
            vo.setTitle(snapshot.getDistill().getAgentQuery().trim());
            vo.setGoal(snapshot.getDistill().getAgentQuery().trim());
        }
        return vo;
    }

    /**
     * 排队上限（PENDING+QUEUED，不含 WAITING 等人确认的任务）：防多 job 风暴。
     * 单 RUNNING 槽本身已串行，上限兜底排队堆积。
     */
    private void assertQueueCapacity(Long userId) {
        int maxQueued = readingRuntimeSettings.maxQueuedJobs();
        long queued = knowledgeReadingJobMapper.selectCountByQuery(QueryWrapper.create()
                .eq("user_id", userId)
                .eq("is_delete", 0)
                .in("status", List.of(
                        KnowledgeReadingJobConstant.STATUS_PENDING,
                        KnowledgeReadingJobConstant.STATUS_RUNNING)));
        if (queued >= maxQueued) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "排队任务已达上限（" + maxQueued + "），请等待现有任务完成后再提交");
        }
    }

    /** 从 resultJson 中取失败来源 URL 列表（partial success 快照） */
    private List<String> failedSourceUrls(KnowledgeReadingJob job) {        if (StrUtil.isBlank(job.getResultJson())) {
            return null;
        }
        try {
            KnowledgeIngestBatchUrlVO result = objectMapper.readValue(job.getResultJson(), KnowledgeIngestBatchUrlVO.class);
            if (result == null || result.getFailedSources() == null || result.getFailedSources().isEmpty()) {
                return null;
            }
            return result.getFailedSources().stream()
                    .map(KnowledgeIngestBatchUrlVO.FailedSource::getUrl)
                    .filter(StrUtil::isNotBlank)
                    .toList();
        } catch (Exception e) {
            log.warn("parse failedSources from job result failed, jobId={}", job.getId(), e);
            return null;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markSuccess(Long jobId, KnowledgeIngestBatchUrlVO result) {
        KnowledgeReadingJob job = knowledgeReadingJobMapper.selectOneById(jobId);
        if (job == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        job.setStatus(KnowledgeReadingJobConstant.STATUS_SUCCESS);
        job.setProgress(KnowledgeReadingJobConstant.PROGRESS_DONE);
        job.setNoteId(result == null ? null : result.getNoteId());
        job.setResultJson(writeJson(result));
        job.setErrorMsg(null);
        job.setFinishedAt(now);
        job.setUpdateTime(now);
        knowledgeReadingJobMapper.update(job);
        readingJobNotifier.notify(job.getUserId(), jobId,
                KnowledgeReadingJobConstant.STATUS_SUCCESS, KnowledgeReadingJobConstant.PROGRESS_DONE);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markFailed(Long jobId, String errorMsg) {
        KnowledgeReadingJob job = knowledgeReadingJobMapper.selectOneById(jobId);
        if (job == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        job.setStatus(KnowledgeReadingJobConstant.STATUS_FAILED);
        job.setProgress(KnowledgeReadingJobConstant.PROGRESS_ERROR);
        job.setErrorMsg(StrUtil.blankToDefault(errorMsg, "精读任务失败"));
        job.setFinishedAt(now);
        job.setUpdateTime(now);
        knowledgeReadingJobMapper.update(job);
        readingJobNotifier.notify(job.getUserId(), jobId,
                KnowledgeReadingJobConstant.STATUS_FAILED, KnowledgeReadingJobConstant.PROGRESS_ERROR);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int failStuckRunning(int timeoutMinutes) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(Math.max(1, timeoutMinutes));
        List<KnowledgeReadingJob> stuck = knowledgeReadingJobMapper.selectListByQuery(QueryWrapper.create()
                .eq("status", KnowledgeReadingJobConstant.STATUS_RUNNING)
                .eq("is_delete", 0)
                .lt("started_at", threshold));
        int n = 0;
        LocalDateTime now = LocalDateTime.now();
        for (KnowledgeReadingJob job : stuck) {
            job.setStatus(KnowledgeReadingJobConstant.STATUS_FAILED);
            job.setProgress(KnowledgeReadingJobConstant.PROGRESS_ERROR);
            job.setErrorMsg("任务执行超时（超过 " + timeoutMinutes + " 分钟），已标记失败");
            job.setFinishedAt(now);
            job.setUpdateTime(now);
            knowledgeReadingJobMapper.update(job);
            readingJobNotifier.notify(job.getUserId(), job.getId(),
                    KnowledgeReadingJobConstant.STATUS_FAILED, KnowledgeReadingJobConstant.PROGRESS_ERROR);
            n++;
        }
        return n;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int failStuckQueued(int timeoutMinutes) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(Math.max(1, timeoutMinutes));
        List<KnowledgeReadingJob> stuck = knowledgeReadingJobMapper.selectListByQuery(QueryWrapper.create()
                .eq("status", KnowledgeReadingJobConstant.STATUS_PENDING)
                .eq("progress", KnowledgeReadingJobConstant.PROGRESS_QUEUED)
                .eq("is_delete", 0)
                .lt("create_time", threshold));
        int n = 0;
        LocalDateTime now = LocalDateTime.now();
        for (KnowledgeReadingJob job : stuck) {
            job.setStatus(KnowledgeReadingJobConstant.STATUS_FAILED);
            job.setProgress(KnowledgeReadingJobConstant.PROGRESS_ERROR);
            job.setErrorMsg("排队超时（超过 " + timeoutMinutes + " 分钟未被调度），请重试");
            job.setFinishedAt(now);
            job.setUpdateTime(now);
            knowledgeReadingJobMapper.update(job);
            readingJobNotifier.notify(job.getUserId(), job.getId(),
                    KnowledgeReadingJobConstant.STATUS_FAILED, KnowledgeReadingJobConstant.PROGRESS_ERROR);
            n++;
        }
        return n;
    }

    @Override
    public KnowledgeReadingJobRequestSnapshot parseSnapshot(KnowledgeReadingJob job) {
        try {
            KnowledgeReadingJobRequestSnapshot snapshot =
                    objectMapper.readValue(job.getRequestJson(), KnowledgeReadingJobRequestSnapshot.class);
            if (snapshot != null && StrUtil.isNotBlank(snapshot.getPhase())) {
                return snapshot;
            }
        } catch (Exception ignored) {
            // 兼容旧任务：request_json 直接是合蒸请求
        }
        try {
            KnowledgeIngestBatchUrlRequest distill =
                    objectMapper.readValue(job.getRequestJson(), KnowledgeIngestBatchUrlRequest.class);
            KnowledgeReadingJobRequestSnapshot snapshot = new KnowledgeReadingJobRequestSnapshot();
            snapshot.setPhase(KnowledgeReadingJobRequestSnapshot.PHASE_DISTILL);
            snapshot.setDistill(distill);
            return snapshot;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务请求解析失败：" + e.getMessage());
        }
    }

    @Override
    public KnowledgeIngestBatchUrlRequest parseRequest(KnowledgeReadingJob job) {
        KnowledgeReadingJobRequestSnapshot snapshot = parseSnapshot(job);
        if (snapshot.getDistill() == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务缺少合蒸请求");
        }
        return snapshot.getDistill();
    }

    @Override
    public void executeSearch(KnowledgeReadingJob job) {
        KnowledgeReadingJobRequestSnapshot snapshot = parseSnapshot(job);
        KnowledgeSearchPreviewRequest search = snapshot.getSearch();
        if (search == null || StrUtil.isBlank(search.getGoal())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "搜索任务缺少 goal");
        }
        KnowledgeSearchPreviewVO vo = new KnowledgeSearchPreviewVO();
        vo.setGoal(search.getGoal());
        boolean treeMode = search.getDomainId() != null;
        if (!treeMode) {
            vo.setOutline(buildOutline(search.getGoal(), search.getPreference()));
        }
        KnowledgeSearchStrategy strategy = resolveSearchStrategy();
        SearchOptions options = SearchOptions.builder().preference(search.getPreference()).build();
        List<SearchResult> results = strategy.search(search.getGoal(), options);
        List<KnowledgeSearchPreviewVO.Candidate> candidates = new ArrayList<>();
        for (SearchResult result : results) {
            KnowledgeSearchPreviewVO.Candidate c = new KnowledgeSearchPreviewVO.Candidate();
            c.setTitle(result.getTitle());
            c.setUrl(result.getUrl());
            c.setSummary(result.getSummary());
            c.setSource(result.getSource());
            c.setScore(result.getScore());
            c.setRecommendReason(result.getRecommendReason());
            c.setRiskFlags(result.getRiskFlags() == null ? List.of() : result.getRiskFlags());
            candidates.add(c);
        }
        // 去重前置：标注「已在库 / 已读」，避免重复学习
        Map<String, Boolean> ingested = sourceDuplicateChecker.checkIngested(
                candidates.stream().map(KnowledgeSearchPreviewVO.Candidate::getUrl).toList(), job.getUserId());
        for (KnowledgeSearchPreviewVO.Candidate c : candidates) {
            c.setAlreadyIngested(Boolean.TRUE.equals(ingested.getOrDefault(c.getUrl(), false)));
        }
        vo.setCandidates(candidates);
        markAwaitingSelect(job.getId(), vo);
    }

    @Override
    public void executeDistill(KnowledgeReadingJob job) {
        KnowledgeIngestBatchUrlRequest request = parseRequest(job);
        Long jobId = job.getId();
        KnowledgeIngestBatchUrlVO result;
        AiUsageCallContext.setJobId(jobId);
        try {
            result = knowledgeMergeDistillService.mergeDistill(
                    request,
                    job.getUserId(),
                    progress -> markProgress(jobId, progress)
            );
        } finally {
            AiUsageCallContext.clearJob();
        }
        markSuccess(jobId, result);
    }

    private List<String> normalizeUrls(KnowledgeIngestBatchUrlRequest request) {
        if (request == null || request.getUrls() == null || request.getUrls().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "urls 不能为空");
        }
        List<String> urls = request.getUrls().stream()
                .filter(StrUtil::isNotBlank)
                .map(String::trim)
                .distinct()
                .toList();
        if (urls.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "urls 不能为空");
        }
        if (urls.size() > KnowledgeReadingJobConstant.MAX_URLS) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "单次最多批量精炼 " + KnowledgeReadingJobConstant.MAX_URLS + " 个 URL");
        }
        if (StrUtil.isNotBlank(request.getDistillPrompt()) && request.getDistillPrompt().length() > 16_000) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "distillPrompt 过长");
        }
        return urls;
    }

    private KnowledgeIngestBatchUrlRequest copyDistillRequest(KnowledgeIngestBatchUrlRequest request, List<String> urls) {
        KnowledgeIngestBatchUrlRequest copy = new KnowledgeIngestBatchUrlRequest();
        copy.setUrls(urls);
        copy.setSourceType(StrUtil.blankToDefault(request.getSourceType(), "AGENT"));
        copy.setAgentQuery(request.getAgentQuery());
        copy.setDistillPrompt(request.getDistillPrompt());
        copy.setTags(request.getTags());
        copy.setOutlineConfirm(request.getOutlineConfirm());
        copy.setOutline(request.getOutline());
        copy.setDomainId(request.getDomainId());
        copy.setGatePassId(request.getGatePassId());
        copy.setBranchId(request.getBranchId());
        copy.setSkipGate(request.getSkipGate());
        return copy;
    }

    private KnowledgeReadingJob requireJob(Long jobId, Long userId) {
        if (jobId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "jobId 不能为空");
        }
        KnowledgeReadingJob job = knowledgeReadingJobMapper.selectOneById(jobId);
        if (job == null || (job.getIsDelete() != null && job.getIsDelete() == 1)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "精读任务不存在");
        }
        if (userId != null && !userId.equals(job.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权查看该精读任务");
        }
        return job;
    }

    private KnowledgeReadingJobVO toPendingVo(KnowledgeReadingJob job) {
        KnowledgeReadingJobVO vo = new KnowledgeReadingJobVO();
        vo.setJobId(job.getId());
        vo.setStatus(job.getStatus());
        vo.setProgress(job.getProgress());
        vo.setSuccess(false);
        vo.setRetryCount(job.getRetryCount() == null ? 0 : job.getRetryCount());
        vo.setCreateTime(job.getCreateTime());
        vo.setStartedAt(job.getStartedAt());
        vo.setFinishedAt(job.getFinishedAt());
        return vo;
    }

    private KnowledgeReadingJobVO toVo(KnowledgeReadingJob job) {
        KnowledgeReadingJobVO vo = toPendingVo(job);
        vo.setErrorMsg(job.getErrorMsg());
        vo.setNoteId(job.getNoteId());
        vo.setRetryExhausted(KnowledgeReadingJobConstant.STATUS_FAILED.equals(job.getStatus())
                && (job.getRetryCount() == null ? 0 : job.getRetryCount()) >= KnowledgeReadingJobConstant.MAX_RETRY);

        if (KnowledgeReadingJobConstant.PROGRESS_AWAITING_OUTLINE.equals(job.getProgress())
                && StrUtil.isNotBlank(job.getResultJson())) {
            vo.setPendingOutline(job.getResultJson());
        }

        if (StrUtil.isNotBlank(job.getRequestJson())) {
            try {
                KnowledgeReadingJobRequestSnapshot snapshot = parseSnapshot(job);
                if (snapshot.getSearch() != null && StrUtil.isNotBlank(snapshot.getSearch().getGoal())) {
                    vo.setGoal(snapshot.getSearch().getGoal().trim());
                    if (StrUtil.isBlank(vo.getTitle())) {
                        vo.setTitle(vo.getGoal());
                    }
                }
                if (snapshot.getDistill() != null) {
                    if (snapshot.getDistill().getUrls() != null) {
                        vo.setTotal(snapshot.getDistill().getUrls().size());
                    }
                    if (StrUtil.isBlank(vo.getTitle()) && StrUtil.isNotBlank(snapshot.getDistill().getAgentQuery())) {
                        vo.setTitle(snapshot.getDistill().getAgentQuery().trim());
                        vo.setGoal(snapshot.getDistill().getAgentQuery().trim());
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (StrUtil.isNotBlank(job.getResultJson())) {
            if (KnowledgeReadingJobConstant.PROGRESS_AWAITING_SELECT.equals(job.getProgress())
                    || KnowledgeReadingJobConstant.STATUS_WAITING.equals(job.getStatus())) {
                try {
                    KnowledgeSearchPreviewVO preview =
                            objectMapper.readValue(job.getResultJson(), KnowledgeSearchPreviewVO.class);
                    fillSearchResult(vo, preview);
                } catch (Exception e) {
                    log.warn("parse search result failed, jobId={}", job.getId(), e);
                }
            } else {
                try {
                    KnowledgeIngestBatchUrlVO result =
                            objectMapper.readValue(job.getResultJson(), KnowledgeIngestBatchUrlVO.class);
                    fillDistillResult(vo, result);
                    vo.setSuccess(KnowledgeReadingJobConstant.STATUS_SUCCESS.equals(job.getStatus()));
                } catch (Exception e) {
                    // 可能仍是搜索结果残留
                    try {
                        KnowledgeSearchPreviewVO preview =
                                objectMapper.readValue(job.getResultJson(), KnowledgeSearchPreviewVO.class);
                        fillSearchResult(vo, preview);
                    } catch (Exception e2) {
                        log.warn("parse reading job result failed, jobId={}", job.getId(), e);
                    }
                }
            }
        }

        if (StrUtil.isBlank(vo.getTitle())) {
            vo.setTitle("任务 #" + job.getId());
        }
        return vo;
    }

    private void fillSearchResult(KnowledgeReadingJobVO vo, KnowledgeSearchPreviewVO preview) {
        if (preview == null) {
            return;
        }
        if (StrUtil.isNotBlank(preview.getGoal())) {
            vo.setGoal(preview.getGoal());
            if (StrUtil.isBlank(vo.getTitle())) {
                vo.setTitle(preview.getGoal());
            }
        }
        vo.setOutline(preview.getOutline());
        if (preview.getCandidates() != null) {
            vo.setCandidates(preview.getCandidates());
            vo.setTotal(preview.getCandidates().size());
        }
    }

    private void fillDistillResult(KnowledgeReadingJobVO vo, KnowledgeIngestBatchUrlVO result) {
        if (result == null) {
            return;
        }
        vo.setNoteId(result.getNoteId());
        vo.setTitle(result.getTitle());
        vo.setTotal(result.getTotal());
        vo.setUsedCount(result.getUsedCount());
        vo.setFailCount(result.getFailCount());
        vo.setWarning(result.getWarning());
        if (result.getUsedSources() != null) {
            vo.setUsedSources(result.getUsedSources());
        }
        if (result.getFailedSources() != null) {
            vo.setFailedSources(result.getFailedSources());
        }
    }

    private String buildOutline(String goal, String preference) {
        String topic = StrUtil.blankToDefault(goal, "该主题");
        try {
            String prompt = """
                    你是学习规划助手。请根据学习目标生成一份简洁的 Markdown 学习大纲（3到6个章节）。
                    不要输出候选网址。学习目标：%s
                    偏好：%s
                    """.formatted(topic, StrUtil.blankToDefault(preference, "无"));
            return aiModelService.chat(prompt);
        } catch (Exception e) {
            return """
                    # %s 学习大纲（占位）

                    1. 基础概念与背景
                    2. 核心原理
                    3. 实践案例
                    4. 常见问题与进阶

                    > 未返回候选时请检查 search.provider 与对应 API Key，或手动粘贴 URL。
                    """.formatted(topic);
        }
    }

    private KnowledgeSearchStrategy resolveSearchStrategy() {
        String provider = readingRuntimeSettings.searchProvider();
        if (provider == null || provider.isBlank()) {
            provider = "deepseek";
        }
        String normalized = provider.trim().toLowerCase();
        for (KnowledgeSearchStrategy strategy : knowledgeSearchStrategies) {
            if (normalized.equalsIgnoreCase(strategy.name())) {
                return strategy;
            }
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR,
                "未知搜索提供方: " + provider + "，当前支持 deepseek, tavily, placeholder");
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务数据序列化失败：" + e.getMessage());
        }
    }
}
