package com.ai.model.vo.worklog;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class WorkLogEntryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long userId;

    private LocalDate workDate;

    /** 前端字段别名 date */
    private String date;

    private String title;

    private String done;

    private String problem;

    private String summary;

    private String plan;

    private List<String> tags;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;

    private Long createdAt;

    private Long updatedAt;
}
