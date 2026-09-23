package com.ai.service.study.support;

import cn.hutool.core.util.StrUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;

/**
 * 学习模块日期工具
 */
public final class StudyDateUtils {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter SPACE_SECOND = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter SPACE_MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final DateTimeFormatter[] DATETIME_FORMATTERS = {
            SPACE_SECOND,
            SPACE_MINUTE,
            ISO_FORMATTER
    };

    private StudyDateUtils() {
    }

    public static String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(SPACE_SECOND);
    }

    public static String formatDate(LocalDate date) {
        return date == null ? null : date.format(DATE_FORMATTER);
    }

    public static LocalDateTime parseDateTime(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        String trimmed = normalizeDateTimeInput(value);
        if (trimmed.length() == 10) {
            return LocalDate.parse(trimmed, DATE_FORMATTER).atStartOfDay();
        }
        for (DateTimeFormatter formatter : DATETIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
                // try next pattern
            }
        }
        // 空格分隔转 ISO：2026-05-31 02:44:00 -> 2026-05-31T02:44:00
        if (trimmed.length() > 10 && trimmed.charAt(10) == ' ') {
            return LocalDateTime.parse(trimmed.substring(0, 10) + 'T' + trimmed.substring(11), ISO_FORMATTER);
        }
        throw new DateTimeParseException("无法解析日期时间: " + value, value, 0);
    }

    /**
     * 规范化日期时间字符串：trim、合并多余空白、全角/不间断空格转普通空格
     */
    static String normalizeDateTimeInput(String value) {
        return value.trim()
                .replace('\u00a0', ' ')
                .replace('\u3000', ' ')
                .replaceAll("\\s+", " ");
    }

    public static LocalDate parseDate(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        return LocalDate.parse(value.trim(), DATE_FORMATTER);
    }

    public static LocalDateTime todayStart() {
        return LocalDate.now().atStartOfDay();
    }

    public static LocalDateTime todayEnd() {
        return LocalDate.now().atTime(LocalTime.MAX);
    }

    public static LocalDateTime weekStart() {
        return LocalDate.now().with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).atStartOfDay();
    }

    public static LocalDateTime weekEnd() {
        return LocalDate.now().with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY)).atTime(LocalTime.MAX);
    }
}
