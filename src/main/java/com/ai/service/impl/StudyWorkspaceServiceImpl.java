package com.ai.service.impl;

import com.ai.model.entity.StudyList;
import com.ai.model.vo.study.StudyFocusSessionVO;
import com.ai.model.vo.study.StudyTodayStatsVO;
import com.ai.model.vo.study.StudyWorkspaceVO;
import com.ai.service.StudyFocusSessionService;
import com.ai.service.StudyListService;
import com.ai.service.StudyRedisCacheService;
import com.ai.service.StudyStatsService;
import com.ai.service.StudyWorkspaceService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudyWorkspaceServiceImpl implements StudyWorkspaceService {

    @Resource
    private StudyListService studyListService;

    @Resource
    private StudyStatsService studyStatsService;

    @Resource
    private StudyFocusSessionService studyFocusSessionService;

    @Resource
    private StudyRedisCacheService studyRedisCacheService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StudyWorkspaceVO initWorkspace(Long userId, boolean createThemeLists) {
        boolean locked = studyRedisCacheService.tryInitLock(userId);
        try {
            studyListService.initDefaultLists(userId, createThemeLists);
        } finally {
            if (locked) {
                studyRedisCacheService.releaseInitLock(userId);
            }
        }

        StudyList inbox = studyListService.getOrCreateInbox(userId);
        StudyTodayStatsVO todayStats = studyStatsService.getTodayStats(userId);
        StudyFocusSessionVO activeFocus = studyFocusSessionService.getActiveFocus(userId);

        StudyWorkspaceVO vo = new StudyWorkspaceVO();
        vo.setInboxListId(inbox.getId());
        vo.setLists(studyListService.getAllLists(userId));
        vo.setTodayStats(todayStats);
        vo.setActiveFocus(activeFocus);
        return vo;
    }
}
