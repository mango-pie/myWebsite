package com.ai.model.vo.knowledge;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class KnowledgeIngestBatchUrlVO {

    private boolean success;
    private Long noteId;
    private String title;
    private int total;
    private int usedCount;
    private int failCount;
    private String warning;
    private List<UsedSource> usedSources = new ArrayList<>();
    private List<FailedSource> failedSources = new ArrayList<>();

    @Data
    public static class UsedSource {
        private String url;
        private String title;
        private Integer bodyChars;
    }

    @Data
    public static class FailedSource {
        private String url;
        private String reasonCode;
        private String errorMessage;
    }
}
