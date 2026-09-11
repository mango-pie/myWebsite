package com.ai.service.knowledge;

import java.util.List;
import java.util.function.Consumer;

public interface KnowledgeAiModelService {

    String chat(String prompt);

    /**
     * 蒸馏等场景可覆盖温度与 maxTokens；systemPrompt 可空。
     */
    String chat(String systemPrompt, String userPrompt, Double temperature, Integer maxTokens);

    void streamChat(String prompt, Consumer<String> onDelta);

    float[] embed(String text);

    List<float[]> embed(List<String> texts);

    String getChatModelName();
}
