package com.ai.ops;

/**
 * 线程级用量埋点上下文：调用 Knowledge AI chat 前设置，结束后 clear。
 */
public final class AiUsageCallContext {

    public record Holder(Long userId, String scene, Long conversationId, String requestSummary) {
    }

    private static final ThreadLocal<Holder> HOLDER = new ThreadLocal<>();

    private AiUsageCallContext() {
    }

    public static void set(Long userId, String scene, Long conversationId, String requestSummary) {
        HOLDER.set(new Holder(userId, scene, conversationId, requestSummary));
    }

    public static Holder get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
