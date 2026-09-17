package com.ai.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 桥接 Logback 与实时日志流：由 Spring 侧在启动时注册 sink，
 * 日志事件经 sink 进入 OpsLogStreamService 的环形缓冲与 SSE 广播。
 * append 路径内严禁打日志，异常一律吞掉，避免递归。
 */
public class LogStreamAppender extends AppenderBase<ILoggingEvent> {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private static final int MAX_MESSAGE = 2000;

    private static volatile Consumer<LogEntry> sink;

    public static void setSink(Consumer<LogEntry> consumer) {
        sink = consumer;
    }

    public static void clearSink() {
        sink = null;
    }

    public LogStreamAppender() {
        setName("opsLogStreamAppender");
    }

    @Override
    protected void append(ILoggingEvent event) {
        Consumer<LogEntry> current = sink;
        if (current == null) {
            return;
        }
        try {
            String message = event.getFormattedMessage();
            if (message != null && message.length() > MAX_MESSAGE) {
                message = message.substring(0, MAX_MESSAGE) + "…";
            }
            String traceId = event.getMDCPropertyMap() == null ? null
                    : event.getMDCPropertyMap().get("traceId");
            StringBuilder throwable = new StringBuilder();
            if (event.getThrowableProxy() != null) {
                throwable.append(event.getThrowableProxy().getClassName())
                        .append(": ")
                        .append(event.getThrowableProxy().getMessage());
            }
            current.accept(new LogEntry(
                    TIME_FORMAT.format(Instant.ofEpochMilli(event.getTimeStamp())),
                    event.getLevel().toString(),
                    event.getThreadName(),
                    shortenLogger(event.getLoggerName()),
                    message,
                    throwable.isEmpty() ? null : throwable.toString(),
                    traceId));
        } catch (Exception ignored) {
            // 日志采集失败不允许影响业务线程
        }
    }

    private String shortenLogger(String loggerName) {
        if (loggerName == null) {
            return "";
        }
        int dot = loggerName.lastIndexOf('.');
        return dot < 0 ? loggerName : loggerName.substring(dot + 1);
    }

    /** 单条日志的展示快照 */
    public record LogEntry(String time, String level, String thread, String logger,
                           String message, String throwable, String traceId) {

        public Map<String, Object> toMap() {
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
            return map;
        }
    }
}
