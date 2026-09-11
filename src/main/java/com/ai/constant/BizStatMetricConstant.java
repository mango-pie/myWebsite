package com.ai.constant;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 业务日统计 metric 白名单（ops-observability Phase C）。
 */
public final class BizStatMetricConstant {

    public static final String DIM_TOTAL = "_";
    public static final int RETAIN_DAYS = 400;

    public static final String BLOG_POST_VIEW = "blog.post.view";
    public static final String BLOG_POST_LIKE = "blog.post.like";
    public static final String BLOG_POST_PUBLISH = "blog.post.publish";
    public static final String CHAT_CONVERSATION_CREATE = "chat.conversation.create";
    public static final String CHAT_MESSAGE_USER = "chat.message.user";
    public static final String READING_INGEST_SUCCESS = "reading.ingest.success";
    public static final String READING_DISTILL_SUCCESS = "reading.distill.success";
    public static final String STUDY_HABIT_CHECKIN = "study.habit.checkin";
    public static final String STUDY_FOCUS_COMPLETE = "study.focus.complete";

    public static final List<String> ALL = List.of(
            BLOG_POST_VIEW,
            BLOG_POST_LIKE,
            BLOG_POST_PUBLISH,
            CHAT_CONVERSATION_CREATE,
            CHAT_MESSAGE_USER,
            READING_INGEST_SUCCESS,
            READING_DISTILL_SUCCESS,
            STUDY_HABIT_CHECKIN,
            STUDY_FOCUS_COMPLETE
    );

    private static final Set<String> ALLOWED = new LinkedHashSet<>(ALL);

    private BizStatMetricConstant() {
    }

    public static boolean isAllowed(String metric) {
        return metric != null && ALLOWED.contains(metric);
    }
}
