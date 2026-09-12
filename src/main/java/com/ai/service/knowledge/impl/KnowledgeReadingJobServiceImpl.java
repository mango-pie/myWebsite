package com.ai.service.knowledge.impl;

import com.ai.config.ConditionalOnModule;

import cn.hutool.core.util.StrUtil;
import com.ai.constant.knowledge.KnowledgeReadingJobConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.mapper.knowledge.KnowledgeReadingJobMapper;
import com.ai.model.dto.knowledge.KnowledgeIngestBatchUrlRequest;
import com.ai.model.entity.knowledge.KnowledgeReadingJob;
import com.ai.model.vo.knowledge.KnowledgeIngestBatchUrlVO;
import com.ai.model.vo.knowledge.KnowledgeReadingJobVO;
import com.ai.service.knowledge.KnowledgeMergeDistillService;
import com.ai.service.knowledge.KnowledgeReadingJobService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

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

    @Override
    public KnowledgeReadingJobVO submit(KnowledgeIngestBatchUrlRequest request, Long userId) {
        List<String> urls = normalizeUrls(request);
        KnowledgeIngestBatchUrlRequest snapshot = copyRequest(request, urls);
        LocalDateTime now = LocalDateTime.now();
        KnowledgeReadingJob job = new KnowledgeReadingJob();
        job.setUserId(userId);
        job.setStatus(KnowledgeReadingJobConstant.STATUS_PENDING);
        job.setProgress(KnowledgeReadingJobConstant.PROGRESS_QUEUED);
        job.setRequestJson(writeJson(snapshot));
        job.setCreateTime(now);
        job.setUpdateTime(now);
        job.setIsDelete(0);
        knowledgeReadingJobMapper.insert(job);

        KnowledgeReadingJobVO vo = toPendingVo(job);
        vo.setTotal(urls.size());
        return vo;
    }

    @Override
    public KnowledgeReadingJobVO getJob(Long jobId, Long userId) {
        KnowledgeReadingJob job = requireJob(jobId, userId);
        return toVo(job);
    }

    @Override
    public KnowledgeReadingJobVO runSync(KnowledgeIngestBatchUrlRequest request, Long userId) {
        KnowledgeIngestBatchUrlVO result = knowledgeMergeDistillService.mergeDistill(request, userId);
        KnowledgeReadingJobVO vo = new KnowledgeReadingJobVO();
        vo.setJobId(null);
        vo.setStatus(KnowledgeReadingJobConstant.STATUS_SUCCESS);
        vo.setProgress(KnowledgeReadingJobConstant.PROGRESS_DONE);
        vo.setSuccess(true);
        fillResult(vo, result);
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
                .eq("is_delete", 0)
                .orderBy("create_time", true)
                .limit(1));
        if (pending == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        KnowledgeReadingJob patch = new KnowledgeReadingJob();
        patch.setStatus(KnowledgeReadingJobConstant.STATUS_RUNNING);
        patch.setProgress(KnowledgeReadingJobConstant.PROGRESS_READING);
        patch.setStartedAt(now);
        patch.setUpdateTime(now);
        // 条件更新：仅当仍为 PENDING 时抢占，避免并发双跑
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
        pending.setProgress(KnowledgeReadingJobConstant.PROGRESS_READING);
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
            n++;
        }
        return n;
    }

    @Override
    public KnowledgeIngestBatchUrlRequest parseRequest(KnowledgeReadingJob job) {
        try {
            return objectMapper.readValue(job.getRequestJson(), KnowledgeIngestBatchUrlRequest.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务请求解析失败：" + e.getMessage());
        }
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

    private KnowledgeIngestBatchUrlRequest copyRequest(KnowledgeIngestBatchUrlRequest request, List<String> urls) {
        KnowledgeIngestBatchUrlRequest copy = new KnowledgeIngestBatchUrlRequest();
        copy.setUrls(urls);
        copy.setSourceType(StrUtil.blankToDefault(request.getSourceType(), "AGENT"));
        copy.setAgentQuery(request.getAgentQuery());
        copy.setDistillPrompt(request.getDistillPrompt());
        copy.setTags(request.getTags());
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
        vo.setCreateTime(job.getCreateTime());
        vo.setStartedAt(job.getStartedAt());
        vo.setFinishedAt(job.getFinishedAt());
        return vo;
    }

    private KnowledgeReadingJobVO toVo(KnowledgeReadingJob job) {
        KnowledgeReadingJobVO vo = toPendingVo(job);
        vo.setErrorMsg(job.getErrorMsg());
        vo.setNoteId(job.getNoteId());
        if (StrUtil.isNotBlank(job.getResultJson())) {
            try {
                KnowledgeIngestBatchUrlVO result =
                        objectMapper.readValue(job.getResultJson(), KnowledgeIngestBatchUrlVO.class);
                fillResult(vo, result);
                vo.setSuccess(KnowledgeReadingJobConstant.STATUS_SUCCESS.equals(job.getStatus()));
            } catch (Exception e) {
                log.warn("parse reading job result failed, jobId={}", job.getId(), e);
            }
        } else if (StrUtil.isNotBlank(job.getRequestJson())) {
            try {
                KnowledgeIngestBatchUrlRequest req =
                        objectMapper.readValue(job.getRequestJson(), KnowledgeIngestBatchUrlRequest.class);
                if (req.getUrls() != null) {
                    vo.setTotal(req.getUrls().size());
                }
            } catch (Exception ignored) {
            }
        }
        return vo;
    }

    private void fillResult(KnowledgeReadingJobVO vo, KnowledgeIngestBatchUrlVO result) {
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

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务数据序列化失败：" + e.getMessage());
        }
    }
}
