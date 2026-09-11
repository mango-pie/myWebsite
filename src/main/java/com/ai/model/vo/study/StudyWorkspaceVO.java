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

    /** 是否默认展示任务清单（来自全站设置 study.workspace.show_checklist） */
    private Boolean showChecklist;
}
