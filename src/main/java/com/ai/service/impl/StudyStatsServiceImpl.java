package com.ai.service.impl;

import com.ai.constant.StudyConstant;
import com.ai.mapper.StudyFocusSessionMapper;
import com.ai.mapper.StudyHabitCheckLogMapper;
import com.ai.mapper.StudyHabitMapper;
import com.ai.mapper.StudyTaskMapper;
import com.ai.model.entity.StudyFocusSession;
import com.ai.model.entity.StudyHabit;
import com.ai.model.entity.StudyHabitCheckLog;
import com.ai.model.entity.StudyTask;
import com.ai.model.vo.study.StudyRangeStatsVO;
import com.ai.model.vo.study.StudyTodayStatsVO;
import com.ai.service.StudyRedisCacheService;
import com.ai.service.StudyStatsService;
import com.ai.utils.StudyDateUtils;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StudyStatsServiceImpl implements StudyStatsService {

    @Resource
    private StudyTaskMapper studyTaskMapper;

    @Resource
    private StudyFocusSessionMapper studyFocusSessionMapper;

    @Resource
    private StudyHabitMapper studyHabitMapper;

    @Resource
    private StudyHabitCheckLogMapper studyHabitCheckLogMapper;

    @Resource
    private StudyRedisCacheService studyRedisCacheService;

    @Override
    public StudyTodayStatsVO getTodayStats(Long userId) {
        return studyRedisCacheService.getTodayStats(userId, () -> loadTodayStats(userId));
    }

    private StudyTodayStatsVO loadTodayStats(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = StudyDateUtils.todayStart();
        LocalDateTime todayEnd = StudyDateUtils.todayEnd();

        long totalTasks = studyTaskMapper.selectCountByQuery(QueryWrapper.create()
                .where("user_id = ?", userId)
                .and("deleted_time IS NULL")
                .and("(due_date <= ? OR is_today = 1 OR (completed_time >= ? AND completed_time <= ?))",
                        todayEnd, todayStart, todayEnd));

        long completedTasks = studyTaskMapper.selectCountByQuery(QueryWrapper.create()
                .where("user_id = ?", userId)
                .and("deleted_time IS NULL")
                .and("status = ?", StudyConstant.TASK_STATUS_DONE)
                .and("completed_time >= ?", todayStart)
                .and("completed_time <= ?", todayEnd));

        long overdueTasks = studyTaskMapper.selectCountByQuery(QueryWrapper.create()
                .where("user_id = ?", userId)
                .and("deleted_time IS NULL")
                .and("status = ?", StudyConstant.TASK_STATUS_PENDING)
                .and("due_date IS NOT NULL")
                .and("due_date < ?", todayStart));

        List<StudyFocusSession> focusSessions = studyFocusSessionMapper.selectListByQuery(QueryWrapper.create()
                .where("user_id = ?", userId)
                .and("status = ?", StudyConstant.FOCUS_STATUS_COMPLETED)
                .and("ended_time >= ?", todayStart)
                .and("ended_time <= ?", todayEnd));
        int focusSeconds = focusSessions.stream()
                .mapToInt(s -> s.getActualSeconds() != null ? s.getActualSeconds() : 0)
                .sum();

        long habitsTotal = studyHabitMapper.selectCountByQuery(QueryWrapper.create()
                .where("user_id = ?", userId)
                .and("deleted_time IS NULL")
                .and("status = ?", 1));

        long habitsChecked = studyHabitCheckLogMapper.selectCountByQuery(QueryWrapper.create()
                .where("user_id = ?", userId)
                .and("check_date = ?", today));

        StudyTodayStatsVO vo = new StudyTodayStatsVO();
        vo.setDate(StudyDateUtils.formatDate(today));
        vo.setTotalTasks((int) totalTasks);
        vo.setCompletedTasks((int) completedTasks);
        vo.setOverdueTasks((int) overdueTasks);
        vo.setFocusMinutes(focusSeconds / 60);
        vo.setHabitsChecked((int) habitsChecked);
        vo.setHabitsTotal((int) habitsTotal);
        return vo;
    }

    @Override
    public StudyRangeStatsVO getRangeStats(Long userId, String startDate, String endDate) {
        LocalDate start = StudyDateUtils.parseDate(startDate);
        LocalDate end = StudyDateUtils.parseDate(endDate);
        LocalDateTime rangeStart = start.atStartOfDay();
        LocalDateTime rangeEnd = end.atTime(23, 59, 59);

        List<StudyTask> completedTasks = studyTaskMapper.selectListByQuery(QueryWrapper.create()
                .where("user_id = ?", userId)
                .and("deleted_time IS NULL")
                .and("status = ?", StudyConstant.TASK_STATUS_DONE)
                .and("completed_time >= ?", rangeStart)
                .and("completed_time <= ?", rangeEnd));

        List<StudyFocusSession> focusSessions = studyFocusSessionMapper.selectListByQuery(QueryWrapper.create()
                .where("user_id = ?", userId)
                .and("status = ?", StudyConstant.FOCUS_STATUS_COMPLETED)
                .and("ended_time >= ?", rangeStart)
                .and("ended_time <= ?", rangeEnd));

        long habitCheckCount = studyHabitCheckLogMapper.selectCountByQuery(QueryWrapper.create()
                .where("user_id = ?", userId)
                .and("check_date >= ?", start)
                .and("check_date <= ?", end));

        Map<String, StudyRangeStatsVO.DailyBreakdown> dailyMap = new HashMap<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            StudyRangeStatsVO.DailyBreakdown breakdown = new StudyRangeStatsVO.DailyBreakdown();
            breakdown.setDate(StudyDateUtils.formatDate(d));
            breakdown.setCompletedTasks(0);
            breakdown.setFocusMinutes(0);
            dailyMap.put(breakdown.getDate(), breakdown);
        }

        for (StudyTask task : completedTasks) {
            String date = StudyDateUtils.formatDate(task.getCompletedTime().toLocalDate());
            StudyRangeStatsVO.DailyBreakdown b = dailyMap.get(date);
            if (b != null) {
                b.setCompletedTasks(b.getCompletedTasks() + 1);
            }
        }
        for (StudyFocusSession session : focusSessions) {
            String date = StudyDateUtils.formatDate(session.getEndedTime().toLocalDate());
            StudyRangeStatsVO.DailyBreakdown b = dailyMap.get(date);
            if (b != null) {
                int seconds = session.getActualSeconds() != null ? session.getActualSeconds() : 0;
                b.setFocusMinutes(b.getFocusMinutes() + seconds / 60);
            }
        }

        int totalFocusSeconds = focusSessions.stream()
                .mapToInt(s -> s.getActualSeconds() != null ? s.getActualSeconds() : 0)
                .sum();

        StudyRangeStatsVO vo = new StudyRangeStatsVO();
        vo.setCompletedTaskCount(completedTasks.size());
        vo.setFocusMinutes(totalFocusSeconds / 60);
        vo.setHabitCheckCount((int) habitCheckCount);
        vo.setDailyBreakdown(new ArrayList<>(dailyMap.values()));
        return vo;
    }
}
