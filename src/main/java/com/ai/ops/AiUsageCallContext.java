package com.ai.ops;

/**
 * 线程级用量埋点上下文：调用 Knowledge AI chat 前设置，结束后 clear。
 * jobId 与 Holder 分离：精读任务执行期间，内部场景（distill/reading）切换 Holder 不会丢失任务归属。
 */
public final class AiUsageCallContext {

    public record Holder(Long userId, String scene, Long conversationId, String requestSummary) {
    }

    private static final ThreadLocal<Holder> HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<Long> JOB_ID = new ThreadLocal<>();

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

    public static void setJobId(Long jobId) {
        JOB_ID.set(jobId);
    }

    public static Long getJobId() {
        return JOB_ID.get();
    }

    public static void clearJob() {
        JOB_ID.remove();
    }
}
