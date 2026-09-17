package com.ai.model.dto.worklog;

import lombok.Data;

import java.util.List;

@Data
public class WorkLogSaveRequest {

    /** 工作日期 yyyy-MM-dd */
    private String workDate;
    private String title;
    private String done;
    private String problem;
    private String summary;
    private String plan;
    private List<String> tags;
}
