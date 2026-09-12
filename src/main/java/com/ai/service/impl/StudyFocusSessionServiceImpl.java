package com.ai.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.ai.constant.StudyConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.mapper.StudyFocusSessionMapper;
import com.ai.model.dto.study.StudyFocusIdRequest;
import com.ai.model.dto.study.StudyFocusStartRequest;
import com.ai.model.entity.StudyFocusSession;
import com.ai.model.entity.StudyTask;
import com.ai.model.vo.study.StudyFocusSessionVO;
import com.ai.service.StudyFocusSessionService;
import com.ai.service.StudyRedisCacheService;
import com.ai.service.StudyTaskService;
import com.ai.utils.StudyDateUtils;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StudyFocusSessionServiceImpl extends ServiceImpl<StudyFocusSessionMapper, StudyFocusSession>
        implements StudyFocusSessionService {

    @Resource
    private StudyTaskService studyTaskService;

    @Resource
    private StudyRedisCacheService studyRedisCacheService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StudyFocusSessionVO startFocus(StudyFocusStartRequest request, Long userId) {
        StudyFocusSessionVO active = findActiveSessionInternal(userId);
        if (active != null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "已有进行中的专注会话");
        }

        if (request.getTaskId() != null) {
            studyTaskService.getOwnedTask(request.getTaskId(), userId);
        }

        int plannedMinutes = request.getPlannedMinutes() != null && request.getPlannedMinutes() > 0
                ? request.getPlannedMinutes() : StudyConstant.DEFAULT_PLANNED_MINUTES;
        int focusType = request.getFocusType() != null ? request.getFocusType() : StudyConstant.FOCUS_TYPE_WORK;

        StudyFocusSession session = new StudyFocusSession();
        session.setUserId(userId);
        session.setTaskId(request.getTaskId());
        session.setFocusType(focusType);
        session.setPlannedMinutes(plannedMinutes);
        session.setActualSeconds(0);
        session.setStatus(StudyConstant.FOCUS_STATUS_RUNNING);
        session.setStartedTime(LocalDateTime.now());
        session.setPauseTotalSeconds(0);
        session.setCreatedTime(LocalDateTime.now());
        session.setUpdatedTime(LocalDateTime.now());
        this.save(session);

        StudyFocusSessionVO vo = toVO(session);
        studyRedisCacheService.setActiveFocus(userId, vo);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StudyFocusSessionVO pauseFocus(StudyFocusIdRequest request, Long userId) {
        StudyFocusSession session = getActiveOwnedSession(request.getId(), userId);
        if (session.getStatus() != StudyConstant.FOCUS_STATUS_RUNNING) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "专注会话不存在或已结束");
        }
        session.setStatus(StudyConstant.FOCUS_STATUS_PAUSED);
        session.setUpdatedTime(LocalDateTime.now());
        this.updateById(session);
        StudyFocusSessionVO vo = toVO(session);
        studyRedisCacheService.setActiveFocus(userId, vo);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StudyFocusSessionVO resumeFocus(StudyFocusIdRequest request, Long userId) {
        StudyFocusSession session = getActiveOwnedSession(request.getId(), userId);
        if (session.getStatus() != StudyConstant.FOCUS_STATUS_PAUSED) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "专注会话不存在或已结束");
        }
        session.setStatus(StudyConstant.FOCUS_STATUS_RUNNING);
        session.setUpdatedTime(LocalDateTime.now());
        this.updateById(session);
        StudyFocusSessionVO vo = toVO(session);
        studyRedisCacheService.setActiveFocus(userId, vo);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StudyFocusSessionVO completeFocus(StudyFocusIdRequest request, Long userId) {
        StudyFocusSession session = getActiveOwnedSession(request.getId(), userId);
        LocalDateTime ended = LocalDateTime.now();
        int actualSeconds = calculateActualSeconds(session, ended);

        session.setStatus(StudyConstant.FOCUS_STATUS_COMPLETED);
        session.setEndedTime(ended);
        session.setActualSeconds(actualSeconds);
        session.setUpdatedTime(ended);
        this.updateById(session);

        studyRedisCacheService.evictActiveFocus(userId);
        studyRedisCacheService.evictTodayStats(userId);
        return toVO(session);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StudyFocusSessionVO abandonFocus(StudyFocusIdRequest request, Long userId) {
        StudyFocusSession session = getActiveOwnedSession(request.getId(), userId);
        LocalDateTime ended = LocalDateTime.now();

        session.setStatus(StudyConstant.FOCUS_STATUS_ABANDONED);
        session.setEndedTime(ended);
        session.setUpdatedTime(ended);
        this.updateById(session);

        studyRedisCacheService.evictActiveFocus(userId);
        studyRedisCacheService.evictTodayStats(userId);
        return toVO(session);
    }

    @Override
    public StudyFocusSessionVO getActiveFocus(Long userId) {
        StudyFocusSessionVO cached = studyRedisCacheService.getActiveFocus(userId);
        if (cached != null) {
            return cached;
        }
        StudyFocusSessionVO active = findActiveSessionInternal(userId);
        if (active != null) {
            studyRedisCacheService.setActiveFocus(userId, active);
        }
        return active;
    }

    @Override
    public Page<StudyFocusSessionVO> listFocusPage(Long userId, int pageNum, int pageSize,
                                                   String startDate, String endDate) {
        if (pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        QueryWrapper qw = QueryWrapper.create()
                .where("user_id = ?", userId)
                .and("status = ?", StudyConstant.FOCUS_STATUS_COMPLETED);
        if (StrUtil.isNotBlank(startDate)) {
            qw.and("started_time >= ?", StudyDateUtils.parseDate(startDate).atStartOfDay());
        }
        if (StrUtil.isNotBlank(endDate)) {
            qw.and("started_time <= ?", StudyDateUtils.parseDate(endDate).atTime(23, 59, 59));
        }
        qw.orderBy("started_time", false);

        Page<StudyFocusSession> page = this.page(Page.of(pageNum, pageSize), qw);
        Page<StudyFocusSessionVO> voPage = new Page<>();
        voPage.setPageNumber(page.getPageNumber());
        voPage.setPageSize(page.getPageSize());
        voPage.setTotalPage(page.getTotalPage());
        voPage.setTotalRow(page.getTotalRow());
        voPage.setRecords(page.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return voPage;
    }

    private StudyFocusSessionVO findActiveSessionInternal(Long userId) {
        StudyFocusSession session = this.getOne(QueryWrapper.create()
                .where("user_id = ?", userId)
                .and("status IN (?, ?)", StudyConstant.FOCUS_STATUS_RUNNING, StudyConstant.FOCUS_STATUS_PAUSED)
                .orderBy("id", false));
        return session == null ? null : toVO(session);
    }

    private StudyFocusSession getActiveOwnedSession(Long id, Long userId) {
        StudyFocusSession session = this.getOne(QueryWrapper.create()
                .where("id = ?", id)
                .and("user_id = ?", userId)
                .and("status IN (?, ?)", StudyConstant.FOCUS_STATUS_RUNNING, StudyConstant.FOCUS_STATUS_PAUSED));
        if (session == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "专注会话不存在或已结束");
        }
        return session;
    }

    private int calculateActualSeconds(StudyFocusSession session, LocalDateTime ended) {
        long total = Duration.between(session.getStartedTime(), ended).getSeconds();
        int pause = session.getPauseTotalSeconds() != null ? session.getPauseTotalSeconds() : 0;
        return (int) Math.max(0, total - pause);
    }

    private StudyFocusSessionVO toVO(StudyFocusSession session) {
        StudyFocusSessionVO vo = new StudyFocusSessionVO();
        BeanUtil.copyProperties(session, vo, "startedTime", "endedTime");
        vo.setStartedTime(StudyDateUtils.formatDateTime(session.getStartedTime()));
        vo.setEndedTime(StudyDateUtils.formatDateTime(session.getEndedTime()));
        if (session.getTaskId() != null) {
            try {
                StudyTask task = studyTaskService.getOwnedTask(session.getTaskId(), session.getUserId());
                vo.setTaskTitle(task.getTitle());
            } catch (BusinessException ignored) {
                // task may have been deleted
            }
        }
        return vo;
    }
}
