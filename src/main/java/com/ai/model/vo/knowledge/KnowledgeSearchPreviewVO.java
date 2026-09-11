package com.ai.model.vo.knowledge;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class KnowledgeSearchPreviewVO {

    private String goal;
    private String outline;
    private List<Candidate> candidates = new ArrayList<>();

    @Data
    public static class Candidate {
        private String title;
        private String url;
        private String summary;
        private String source;
        private Double score;
        private String recommendReason;
        private java.util.List<String> riskFlags = new java.util.ArrayList<>();
    }
}
