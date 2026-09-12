package com.ai.model.vo.worklog;

import java.io.Serializable;
import lombok.Data;

@Data
public class WorkLogImportResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private int imported;

    private int skipped;
}
