package com.ai.model.dto.worklog;

import lombok.Data;

import java.util.List;

@Data
public class WorkLogImportRequest {

    /** 命中同日期已有记录时是否覆盖 */
    private Boolean overwrite;

    private List<WorkLogSaveRequest> entries;
}
