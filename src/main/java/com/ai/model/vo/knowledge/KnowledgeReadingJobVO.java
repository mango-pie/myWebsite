package com.ai.model.vo.knowledge;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class KnowledgeReadingJobVO {

    private Long jobId;
    private String status;
    private String progress;
    private boolean success;
    private Long noteId;
    private String title;
    private int total;
    private int usedCount;
    private int failCount;
    private String warning;
    private String errorMsg;
    private List<KnowledgeIngestBatchUrlVO.UsedSource> usedSources = new ArrayList<>();
    private List<KnowledgeIngestBatchUrlVO.FailedSource> failedSources = new ArrayList<>();
    /** 搜索阶段：学习目标 */
    private String goal;
    /** 搜索阶段：大纲（非树模式） */
    private String outline;
    /** 大纲确认步：等待用户确认的大纲（AWAITING_OUTLINE） */
    private String pendingOutline;
    /** 失败任务已重试次数 */
    private int retryCount;
    /** 是否已达重试上限 */
    private boolean retryExhausted;
    /** 搜索阶段：候选列表（AWAITING_SELECT） */
    private List<KnowledgeSearchPreviewVO.Candidate> candidates = new ArrayList<>();
    private LocalDateTime createTime;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
