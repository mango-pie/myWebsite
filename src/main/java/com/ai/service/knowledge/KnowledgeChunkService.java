package com.ai.service.knowledge;

import com.ai.model.vo.knowledge.KnowledgeTextChunk;

import java.util.List;

public interface KnowledgeChunkService {

    List<KnowledgeTextChunk> split(String text);
}
