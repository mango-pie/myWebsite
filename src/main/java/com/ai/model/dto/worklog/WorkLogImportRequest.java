package com.ai.model.dto.worklog;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

@Data
public class WorkLogImportRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** false 时跳过已存在日期 */
    private Boolean overwrite = true;

    private List<WorkLogSaveRequest> entries;
}
