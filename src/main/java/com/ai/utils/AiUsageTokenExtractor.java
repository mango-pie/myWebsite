package com.ai.utils;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

/**
 * 从 AI 响应中提取 Token 用量。
 */
public final class AiUsageTokenExtractor {

    public record Tokens(Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        public static Tokens empty() {
            return new Tokens(null, null, null);
        }
    }

    private AiUsageTokenExtractor() {
    }

    public static Tokens from(ChatResponse response) {
        if (response == null || response.tokenUsage() == null) {
            return Tokens.empty();
        }
        TokenUsage usage = response.tokenUsage();
        Integer prompt = usage.inputTokenCount();
        Integer completion = usage.outputTokenCount();
        Integer total = usage.totalTokenCount();
        if (total == null && prompt != null && completion != null) {
            total = prompt + completion;
        }
        return new Tokens(prompt, completion, total);
    }

    public static Tokens fromOpenAiUsage(JsonNode usageNode) {
        if (usageNode == null || usageNode.isMissingNode() || usageNode.isNull()) {
            return Tokens.empty();
        }
        Integer prompt = readInt(usageNode, "prompt_tokens");
        Integer completion = readInt(usageNode, "completion_tokens");
        Integer total = readInt(usageNode, "total_tokens");
        if (total == null && prompt != null && completion != null) {
            total = prompt + completion;
        }
        return new Tokens(prompt, completion, total);
    }

    private static Integer readInt(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value == null || value.isMissingNode() || value.isNull() || !value.isNumber()) {
            return null;
        }
        return value.asInt();
    }
}
