package com.ai.model.dto.worklog;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;

@Data
public class WorkLogSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private LocalDate workDate;

    private String title;

    private String done;

    private String problem;

    private String summary;

    private String plan;

    private List<String> tags;
}
