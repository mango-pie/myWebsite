package com.ai.model.dto.knowledge;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SearchOptions {

    private Integer maxResults;
    private String preference;
}
