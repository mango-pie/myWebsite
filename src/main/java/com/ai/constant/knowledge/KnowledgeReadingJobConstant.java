package com.ai.constant.knowledge;

public final class KnowledgeReadingJobConstant {

    private KnowledgeReadingJobConstant() {
    }

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    public static final String PROGRESS_QUEUED = "QUEUED";
    public static final String PROGRESS_READING = "READING";
    public static final String PROGRESS_DISTILLING = "DISTILLING";
    public static final String PROGRESS_DONE = "DONE";
    public static final String PROGRESS_ERROR = "ERROR";

    public static final int MAX_URLS = 5;
    public static final int STUCK_TIMEOUT_MINUTES = 20;
}
