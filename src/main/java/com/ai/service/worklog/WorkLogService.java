package com.ai.service.worklog;

import com.ai.model.dto.worklog.WorkLogImportRequest;
import com.ai.model.dto.worklog.WorkLogSaveRequest;
import com.ai.model.vo.worklog.WorkLogEntryVO;
import com.ai.model.vo.worklog.WorkLogImportResultVO;

import java.util.List;

public interface WorkLogService {

    WorkLogEntryVO save(WorkLogSaveRequest request, Long userId);

    List<WorkLogEntryVO> list(Integer limit, Long userId);

    WorkLogEntryVO byDate(String date, Long userId);

    boolean delete(String date, Long userId);

    WorkLogImportResultVO importEntries(WorkLogImportRequest request, Long userId);
}
