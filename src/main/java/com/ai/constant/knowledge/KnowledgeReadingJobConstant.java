package com.ai.constant.knowledge;

public final class KnowledgeReadingJobConstant {

    private KnowledgeReadingJobConstant() {
    }

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    /** 等人勾选候选 / 确认大纲，不占用全局 RUNNING 槽位 */
    public static final String STATUS_WAITING = "WAITING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    public static final String PROGRESS_SEARCHING = "SEARCHING";
    public static final String PROGRESS_AWAITING_SELECT = "AWAITING_SELECT";
    /** 合蒸大纲已生成，等人确认后才开始蒸馏 */
    public static final String PROGRESS_AWAITING_OUTLINE = "AWAITING_OUTLINE";
    public static final String PROGRESS_QUEUED = "QUEUED";
    public static final String PROGRESS_READING = "READING";
    public static final String PROGRESS_DISTILLING = "DISTILLING";
    public static final String PROGRESS_DONE = "DONE";
    public static final String PROGRESS_ERROR = "ERROR";

    public static final int MAX_URLS = 5;
    /** 失败任务最大重试次数 */
    public static final int MAX_RETRY = 1;
    /** RUNNING 卡死判定（分钟） */
    public static final int STUCK_TIMEOUT_MINUTES = 20;
    /** QUEUED 排队超时判定（分钟） */
    public static final int QUEUED_TIMEOUT_MINUTES = 30;
}
