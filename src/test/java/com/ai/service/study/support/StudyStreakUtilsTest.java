package com.ai.service.study.support;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StudyStreakUtilsTest {

    @Test
    void calculateStreak_emptyReturnsZero() {
        assertEquals(0, StudyStreakUtils.calculateStreak(Collections.emptyList()));
    }

    @Test
    void calculateStreak_consecutiveDays() {
        LocalDate today = LocalDate.of(2026, 5, 30);
        List<LocalDate> dates = Arrays.asList(
                today,
                today.minusDays(1),
                today.minusDays(2),
                today.minusDays(5)
        );
        assertEquals(3, StudyStreakUtils.calculateStreak(dates));
    }

    @Test
    void calculateStreak_singleDay() {
        LocalDate today = LocalDate.of(2026, 5, 30);
        assertEquals(1, StudyStreakUtils.calculateStreak(List.of(today)));
    }

    @Test
    void latestCheckDate_returnsMax() {
        LocalDate d1 = LocalDate.of(2026, 5, 28);
        LocalDate d2 = LocalDate.of(2026, 5, 30);
        assertEquals(d2, StudyStreakUtils.latestCheckDate(Arrays.asList(d1, d2)));
    }
}
