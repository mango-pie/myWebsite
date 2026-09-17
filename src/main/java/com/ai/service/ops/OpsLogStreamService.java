package com.ai.service.ops;

import com.ai.config.ConditionalOnModule;

import cn.hutool.json.JSONUtil;
import com.ai.log.LogStreamAppender;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * AstrBot 式实时日志流：环形缓冲最近 N 条 + SSE 广播给订阅的 Admin 前端。
 * 除系统日志外，审计事件（OpsAuditLogServiceImpl）也汇入同一条流。
 */
@Slf4j
@ConditionalOnModule("ops")
@Service
public class OpsLogStreamService {

    /** 回放缓冲条数 */
    private static final int BACKLOG_CAPACITY = 500;
    /** 单条 SSE 心跳间隔（秒）由 @Scheduled 驱动 */
    private static final long HEARTBEAT_SECONDS = 15;

    private static final int LEVEL_DEBUG = 1;
    private static final int LEVEL_INFO = 2;
    private static final int LEVEL_WARN = 3;
    private static final int LEVEL_ERROR = 4;

    private final Deque<LogStreamMessage> backlog = new ArrayDeque<>(BACKLOG_CAPACITY);
    private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();

    private record Subscriber(SseEmitter emitter, int minLevel) {
    }

    /** 一条待展示日志：系统日志或审计事件 */
    public record LogStreamMessage(String time, String level, String thread, String logger,
                                   String message, String throwable, String traceId, String kind) {

        public static LogStreamMessage of(LogStreamAppender.LogEntry entry) {
            return new LogStreamMessage(entry.time(), entry.level(), entry.thread(), entry.logger(),
                    entry.message(), entry.throwable(), entry.traceId(), "log");
        }

        public static LogStreamMessage audit(String time, String summary, boolean success) {
            return new LogStreamMessage(time, success ? "INFO" : "WARN", "audit", "AUDIT",
                    summary, null, null, "audit");
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("time", time);
            map.put("level", level);
            map.put("thread", thread);
            map.put("logger", logger);
            map.put("message", message);
            if (throwable != null) {
                map.put("throwable", throwable);
            }
            if (traceId != null) {
                map.put("traceId", traceId);
            }
            map.put("kind", kind);
            return map;
        }
    }

    public OpsLogStreamService() {
        LogStreamAppender.setSink(this::accept);
        log.info("Ops log stream service started (backlog={}, heartbeat={}s)",
                BACKLOG_CAPACITY, HEARTBEAT_SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        LogStreamAppender.clearSink();
        for (Subscriber subscriber : subscribers) {
            try {
                subscriber.emitter().complete();
            } catch (Exception ignored) {
                // 忽略关闭异常
            }
        }
        subscribers.clear();
    }

    /** 接收一条日志事件（Logback appender / 审计埋点调用），绝不抛异常、绝不打日志 */
    public void accept(LogStreamMessage message) {
        if (message == null) {
            return;
        }
        synchronized (backlog) {
            if (backlog.size() >= BACKLOG_CAPACITY) {
                backlog.pollFirst();
            }
            backlog.addLast(message);
        }
        String payload = JSONUtil.toJsonStr(message.toMap());
        for (Subscriber subscriber : subscribers) {
            if (levelValue(message.level()) < subscriber.minLevel()) {
                continue;
            }
            send(subscriber.emitter(), "log", payload);
        }
    }

    public void accept(LogStreamAppender.LogEntry entry) {
        if (entry != null) {
            accept(LogStreamMessage.of(entry));
        }
    }

    /** Admin SSE 订阅：先回放缓冲，再持续推送 */
    public SseEmitter subscribe(int minLevel) {
        SseEmitter emitter = new SseEmitter(0L);
        Subscriber subscriber = new Subscriber(emitter, Math.max(LEVEL_DEBUG, minLevel));
        List<LogStreamMessage> replay = new ArrayList<>();
        synchronized (backlog) {
            replay.addAll(backlog);
        }
        try {
            for (LogStreamMessage message : replay) {
                if (levelValue(message.level()) >= subscriber.minLevel()) {
                    emitter.send(SseEmitter.event().name("log")
                            .data(JSONUtil.toJsonStr(message.toMap())));
                }
            }
            emitter.send(SseEmitter.event().name("ready").data("ok"));
        } catch (Exception e) {
            emitter.completeWithError(e);
            return emitter;
        }
        subscribers.add(subscriber);
        Runnable cleanup = () -> subscribers.remove(subscriber);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());
        return emitter;
    }

    /** SSE 心跳，防止代理断开长连接 */
    @Scheduled(fixedDelay = HEARTBEAT_SECONDS * 1000)
    public void heartbeat() {
        if (subscribers.isEmpty()) {
            return;
        }
        for (Subscriber subscriber : subscribers) {
            send(subscriber.emitter(), "ping", String.valueOf(System.currentTimeMillis()));
        }
    }

    private void send(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (Exception e) {
            subscribers.removeIf(s -> s.emitter() == emitter);
        }
    }

    private int levelValue(String level) {
        return switch (level == null ? "INFO" : level.toUpperCase()) {
            case "DEBUG", "TRACE" -> LEVEL_DEBUG;
            case "WARN", "WARNING" -> LEVEL_WARN;
            case "ERROR", "FATAL" -> LEVEL_ERROR;
            default -> LEVEL_INFO;
        };
    }

    public static final int MIN_LEVEL_DEBUG = LEVEL_DEBUG;
    public static final int MIN_LEVEL_INFO = LEVEL_INFO;
}
