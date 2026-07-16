package com.ai.constant;

/**
 * AI 用量场景（ops-observability Phase A）。
 */
public final class AiUsageSceneConstant {

    public static final String KNOWLEDGE_CHAT = "knowledge_chat";
    public static final String DISTILL = "distill";
    public static final String AGENT = "agent";
    public static final String CODEGEN = "codegen";
    public static final String CAPTION = "caption";
    public static final String SEGMENTATION = "segmentation";
    public static final String OTHER = "other";

    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_ERROR = "error";

    private AiUsageSceneConstant() {
    }
}
