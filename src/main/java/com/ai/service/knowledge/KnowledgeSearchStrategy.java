package com.ai.service.knowledge;

import com.ai.model.dto.knowledge.SearchOptions;
import com.ai.model.vo.knowledge.SearchResult;

import java.util.List;

public interface KnowledgeSearchStrategy {

    String name();

    List<SearchResult> search(String query, SearchOptions options);
}
