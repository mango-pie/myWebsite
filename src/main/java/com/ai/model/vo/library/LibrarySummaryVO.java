package com.ai.model.vo.library;

import lombok.Data;

@Data
public class LibrarySummaryVO {

    private long fileCount;
    private long usedBytes;
    private long quotaBytes;
    private Long recentFileId;
    /** yyyy-MM-dd HH:mm */
    private String recentFileAt;
}
