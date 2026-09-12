package com.ai.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.ai.constant.StudyConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.mapper.StudyHabitCheckLogMapper;
import com.ai.mapper.StudyHabitMapper;
import com.ai.model.dto.study.StudyHabitAddRequest;
import com.ai.model.dto.study.StudyHabitCheckRequest;
import com.ai.model.dto.study.StudyHabitUpdateRequest;
import com.ai.model.entity.StudyHabit;
import com.ai.model.entity.StudyHabitCheckLog;
import com.ai.model.vo.study.StudyHabitVO;
import com.ai.service.StudyHabitService;
import com.ai.service.StudyRedisCacheService;
import com.ai.utils.StudyDateUtils;
import com.ai.utils.StudyStreakUtils;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class StudyHabitServiceImpl extends ServiceImpl<StudyHabitMapper, StudyHabit> implements StudyHabitService {

    @Resource
    private StudyHabitCheckLogMapper studyHabitCheckLogMapper;

    @Resource
    private StudyRedisCacheService studyRedisCacheService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long addHabit(StudyHabitAddRequest request, Long userId) {
        if (request == null || StrUtil.isBlank(request.getTitle())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        StudyHabit habit = new StudyHabit();
        habit.setUserId(userId);
        habit.setTitle(request.getTitle().trim());
        habit.setDescription(request.getDescription());
        habit.setIcon(request.getIcon());
        habit.setColor(request.getColor());
        habit.setTargetDaysPerWeek(request.getTargetDaysPerWeek() != null ? request.getTargetDaysPerWeek() : 7);
        habit.setStreakCount(0);
        habit.setBestStreak(0);
        habit.setSortOrder(0);
        habit.setStatus(1);
        habit.setCreatedTime(LocalDateTime.now());
        habit.setUpdatedTime(LocalDateTime.now());
        if (!this.save(habit)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "创建习惯失败");
        }
        studyRedisCacheService.evictTodayStats(userId);
        return habit.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateHabit(StudyHabitUpdateRequest request, Long userId) {
        getOwnedHabit(request.getId(), userId);
        StudyHabit update = new StudyHabit();
        update.setId(request.getId());
        if (request.getTitle() != null) {
            update.setTitle(request.getTitle().trim());
        }
        if (request.getDescription() != null) {
            update.setDescription(request.getDescription());
        }
        if (request.getIcon() != null) {
            update.setIcon(request.getIcon());
        }
        if (request.getColor() != null) {
            update.setColor(request.getColor());
        }
        if (request.getTargetDaysPerWeek() != null) {
            update.setTargetDaysPerWeek(request.getTargetDaysPerWeek());
        }
        if (request.getSortOrder() != null) {
            update.setSortOrder(request.getSortOrder());
        }
        if (request.getStatus() != null) {
            update.setStatus(request.getStatus());
        }
        update.setUpdatedTime(LocalDateTime.now());
        return this.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteHabit(Long id, Long userId) {
        getOwnedHabit(id, userId);
        StudyHabit update = new StudyHabit();
        update.setId(id);
        update.setDeletedTime(LocalDateTime.now());
        update.setUpdatedTime(LocalDateTime.now());
        studyRedisCacheService.evictTodayStats(userId);
        return this.updateById(update);
    }

    @Override
    public List<StudyHabitVO> listAllHabits(Long userId) {
        List<StudyHabit> habits = this.list(QueryWrapper.create()
                .where("user_id = ?", userId)
                .and("deleted_time IS NULL")
                .orderBy("sort_order", true)
                .orderBy("id", true));
        LocalDate today = LocalDate.now();
        LocalDate weekStart = StudyDateUtils.weekStart().toLocalDate();
        LocalDate weekEnd = StudyDateUtils.weekEnd().toLocalDate();

        return habits.stream().map(h -> {
            StudyHabitVO vo = toVO(h);
            List<StudyHabitCheckLog> logs = studyHabitCheckLogMapper.selectListByQuery(QueryWrapper.create()
                    .where("habit_id = ?", h.getId())
                    .and("user_id = ?", userId));
            Set<LocalDate> dates = logs.stream().map(StudyHabitCheckLog::getCheckDate).collect(Collectors.toSet());
            vo.setCheckedToday(dates.contains(today));
            long weekCount = dates.stream()
                    .filter(d -> !d.isBefore(weekStart) && !d.isAfter(weekEnd))
                    .count();
            vo.setWeekCheckedDays((int) weekCount);
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean checkHabit(StudyHabitCheckRequest request, Long userId) {
        if (request == null || request.getHabitId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        StudyHabit habit = getOwnedHabit(request.getHabitId(), userId);
        LocalDate checkDate = request.getCheckDate() != null
                ? StudyDateUtils.parseDate(request.getCheckDate()) : LocalDate.now();

        StudyHabitCheckLog existing = studyHabitCheckLogMapper.selectOneByQuery(QueryWrapper.create()
                .where("habit_id = ?", habit.getId())
                .and("check_date = ?", checkDate));
        if (existing == null) {
            StudyHabitCheckLog log = new StudyHabitCheckLog();
            log.setHabitId(habit.getId());
            log.setUserId(userId);
            log.setCheckDate(checkDate);
            log.setCreatedTime(LocalDateTime.now());
            studyHabitCheckLogMapper.insert(log);
        }
        recalculateStreak(habit.getId(), userId);
        studyRedisCacheService.evictTodayStats(userId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean uncheckHabit(StudyHabitCheckRequest request, Long userId) {
        if (request == null || request.getHabitId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        StudyHabit habit = getOwnedHabit(request.getHabitId(), userId);
        LocalDate checkDate = request.getCheckDate() != null
                ? StudyDateUtils.parseDate(request.getCheckDate()) : LocalDate.now();

        StudyHabitCheckLog existing = studyHabitCheckLogMapper.selectOneByQuery(QueryWrapper.create()
                .where("habit_id = ?", habit.getId())
                .and("check_date = ?", checkDate));
        if (existing == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "习惯今日未打卡，无法取消");
        }
        studyHabitCheckLogMapper.deleteById(existing.getId());
        recalculateStreak(habit.getId(), userId);
        studyRedisCacheService.evictTodayStats(userId);
        return true;
    }

    @Override
    public List<String> getCheckCalendar(Long habitId, Long userId, int year, int month) {
        getOwnedHabit(habitId, userId);
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        List<StudyHabitCheckLog> logs = studyHabitCheckLogMapper.selectListByQuery(QueryWrapper.create()
                .where("habit_id = ?", habitId)
                .and("user_id = ?", userId)
                .and("check_date >= ?", start)
                .and("check_date <= ?", end));
        return logs.stream()
                .map(l -> StudyDateUtils.formatDate(l.getCheckDate()))
                .sorted()
                .collect(Collectors.toList());
    }

    private void recalculateStreak(Long habitId, Long userId) {
        List<StudyHabitCheckLog> logs = studyHabitCheckLogMapper.selectListByQuery(QueryWrapper.create()
                .where("habit_id = ?", habitId)
                .and("user_id = ?", userId)
                .orderBy("check_date", false));

        List<LocalDate> dates = logs.stream()
                .map(StudyHabitCheckLog::getCheckDate)
                .collect(Collectors.toList());

        int streak = StudyStreakUtils.calculateStreak(dates);
        LocalDate lastCheck = StudyStreakUtils.latestCheckDate(dates);

        StudyHabit habit = this.getById(habitId);
        int best = habit.getBestStreak() != null ? habit.getBestStreak() : 0;
        if (streak > best) {
            best = streak;
        }

        StudyHabit update = new StudyHabit();
        update.setId(habitId);
        update.setStreakCount(streak);
        update.setBestStreak(best);
        update.setLastCheckDate(lastCheck);
        update.setUpdatedTime(LocalDateTime.now());
        this.updateById(update);
    }

    private StudyHabit getOwnedHabit(Long id, Long userId) {
        StudyHabit habit = this.getOne(QueryWrapper.create()
                .where("id = ?", id)
                .and("user_id = ?", userId)
                .and("deleted_time IS NULL"));
        if (habit == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "习惯不存在");
        }
        return habit;
    }

    private StudyHabitVO toVO(StudyHabit habit) {
        StudyHabitVO vo = new StudyHabitVO();
        BeanUtil.copyProperties(habit, vo, "lastCheckDate");
        vo.setLastCheckDate(StudyDateUtils.formatDate(habit.getLastCheckDate()));
        return vo;
    }
}
