package com.ai.model.dto.worklog;

import lombok.Data;

@Data
public class WorkLogDeleteRequest {

    /** 工作日期 yyyy-MM-dd */
    private String date;
}
