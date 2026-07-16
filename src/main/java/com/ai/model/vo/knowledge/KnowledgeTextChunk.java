package com.ai.model.vo.knowledge;

public record KnowledgeTextChunk(
        Integer chunkIndex,
        String heading,
        String content,
        Integer tokenCount,
        String metadata
) {
}
