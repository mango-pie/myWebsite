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
    private LocalDateTime createTime;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
