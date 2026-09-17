package com.ai.model.vo.worklog;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WorkLogEntryVO {

    private Long id;
    /** yyyy-MM-dd */
    private String workDate;
    private String title;
    private String done;
    private String problem;
    private String summary;
    private String plan;
    private List<String> tags = new ArrayList<>();
    /** yyyy-MM-dd HH:mm */
    private String createdAt;
    private String updatedAt;
}
