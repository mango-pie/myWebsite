package com.ai.service.study.support;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 习惯连续打卡天数计算
 */
public final class StudyStreakUtils {

    private StudyStreakUtils() {
    }

    public static int calculateStreak(List<LocalDate> checkDates) {
        if (checkDates == null || checkDates.isEmpty()) {
            return 0;
        }
        List<LocalDate> sorted = checkDates.stream()
                .distinct()
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());

        int streak = 1;
        LocalDate cursor = sorted.get(0);
        for (int i = 1; i < sorted.size(); i++) {
            LocalDate date = sorted.get(i);
            if (date.equals(cursor.minusDays(1))) {
                streak++;
                cursor = date;
            } else {
                break;
            }
        }
        return streak;
    }

    public static LocalDate latestCheckDate(List<LocalDate> checkDates) {
        if (checkDates == null || checkDates.isEmpty()) {
            return null;
        }
        return checkDates.stream().max(LocalDate::compareTo).orElse(null);
    }
}
