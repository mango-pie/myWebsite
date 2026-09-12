package com.ai.service;

import com.ai.model.dto.worklog.WorkLogImportRequest;
import com.ai.model.dto.worklog.WorkLogSaveRequest;
import com.ai.model.entity.WorkLogEntry;
import com.ai.model.vo.worklog.WorkLogEntryVO;
import com.ai.model.vo.worklog.WorkLogImportResultVO;
import com.mybatisflex.core.service.IService;
import java.time.LocalDate;
import java.util.List;

public interface WorkLogEntryService extends IService<WorkLogEntry> {

    long saveEntry(WorkLogSaveRequest request, Long userId);

    WorkLogEntryVO getByDate(LocalDate date, Long userId);

    boolean deleteByDate(LocalDate date, Long userId);

    List<WorkLogEntryVO> listRecent(Long userId, int limit);

    List<LocalDate> listDatesInMonth(int year, int month, Long userId);

    WorkLogImportResultVO importEntries(WorkLogImportRequest request, Long userId);
}
