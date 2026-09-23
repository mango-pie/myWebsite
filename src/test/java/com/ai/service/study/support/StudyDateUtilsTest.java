package com.ai.service.study.support;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class StudyDateUtilsTest {

    /**
     * 复现线上报错字符串，控制台打印解析结果便于联调确认
     */
    @Test
    void parseDateTime_reproduceProductionError() {
        String input = "2026-05-31 02:44:00";
        LocalDateTime result = StudyDateUtils.parseDateTime(input);
        System.out.println("[StudyDateUtilsTest] parseDateTime(\"" + input + "\") => " + result);
        assertNotNull(result);
        assertEquals(2026, result.getYear());
        assertEquals(5, result.getMonthValue());
        assertEquals(31, result.getDayOfMonth());
        assertEquals(2, result.getHour());
        assertEquals(44, result.getMinute());
        assertEquals(0, result.getSecond());
    }

    @Test
    void parseDateTime_spaceSeparated() {
        LocalDateTime result = StudyDateUtils.parseDateTime("2026-05-31 23:42:00");
        assertNotNull(result);
        assertEquals(23, result.getHour());
        assertEquals(42, result.getMinute());
    }

    @Test
    void parseDateTime_isoWithT() {
        LocalDateTime result = StudyDateUtils.parseDateTime("2026-05-31T23:42:00");
        assertEquals(23, result.getHour());
    }

    @Test
    void parseDateTime_isoDateOnly() {
        LocalDateTime result = StudyDateUtils.parseDateTime("2026-05-30");
        assertNotNull(result);
        assertEquals(0, result.getHour());
    }

    @Test
    void normalizeDateTimeInput_collapsesSpaces() {
        assertEquals("2026-05-31 02:44:00",
                StudyDateUtils.normalizeDateTimeInput("  2026-05-31  02:44:00  "));
    }

    @Test
    void todayEnd_isEndOfDay() {
        LocalDateTime end = StudyDateUtils.todayEnd();
        assertTrue(end.getHour() >= 23);
    }

    @Test
    void weekStart_isMonday() {
        assertEquals(java.time.DayOfWeek.MONDAY, StudyDateUtils.weekStart().getDayOfWeek());
    }
}
