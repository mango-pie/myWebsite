package com.ai.model.vo.knowledge;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class SearchResult {

    private String title;
    private String url;
    private String summary;
    private String source;
    private Double score;
    private String recommendReason;
    @Builder.Default
    private List<String> riskFlags = new ArrayList<>();
}
