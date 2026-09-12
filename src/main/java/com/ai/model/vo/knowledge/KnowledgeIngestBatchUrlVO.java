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
    /** 与学习目标对齐度 0.00~1.00（对齐度自检结果） */
    private Double alignmentScore;
    private String alignmentNote;
    private List<UsedSource> usedSources = new ArrayList<>();
    private List<FailedSource> failedSources = new ArrayList<>();

    @Data
    public static class UsedSource {
        private String url;
        private String title;
        /** 句级溯源标记，如 src1，对应蒸馏稿中的 [src1] */
        private String srcRef;
        private Integer bodyChars;
    }

    @Data
    public static class FailedSource {
        private String url;
        private String reasonCode;
        private String errorMessage;
    }
}
