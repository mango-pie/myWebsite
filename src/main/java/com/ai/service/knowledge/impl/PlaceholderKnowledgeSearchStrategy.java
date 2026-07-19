package com.ai.service.knowledge.impl;

import com.ai.config.ConditionalOnModule;

import com.ai.model.dto.knowledge.SearchOptions;
import com.ai.model.vo.knowledge.SearchResult;
import com.ai.service.knowledge.KnowledgeSearchStrategy;
import org.springframework.stereotype.Service;

import java.util.List;

@ConditionalOnModule("knowledge")
@Service
public class PlaceholderKnowledgeSearchStrategy implements KnowledgeSearchStrategy {

    @Override
    public String name() {
        return "placeholder";
    }

    @Override
    public List<SearchResult> search(String query, SearchOptions options) {
        return List.of();
    }
}
