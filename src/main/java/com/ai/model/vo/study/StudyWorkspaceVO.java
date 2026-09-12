package com.ai.model.vo.study;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class StudyWorkspaceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long inboxListId;

    private List<StudyListVO> lists;

    private StudyTodayStatsVO todayStats;

    private StudyFocusSessionVO activeFocus;
}
