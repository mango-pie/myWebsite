package com.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.ai.config.ConditionalOnModule;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.mapper.worklog.WorkLogEntryMapper;
import com.ai.model.dto.worklog.WorkLogImportRequest;
import com.ai.model.dto.worklog.WorkLogSaveRequest;
import com.ai.model.entity.WorkLogEntry;
import com.ai.model.vo.worklog.WorkLogEntryVO;
import com.ai.model.vo.worklog.WorkLogImportResultVO;
import com.ai.service.WorkLogEntryService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@ConditionalOnModule("worklog")
@Service
public class WorkLogEntryServiceImpl extends ServiceImpl<WorkLogEntryMapper, WorkLogEntry>
        implements WorkLogEntryService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long saveEntry(WorkLogSaveRequest request, Long userId) {
        if (request == null || userId == null || request.getWorkDate() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        LocalDateTime now = LocalDateTime.now();
        WorkLogEntry existing = findActive(userId, request.getWorkDate());
        if (existing != null) {
            applyFields(existing, request);
            existing.setUpdatedTime(now);
            if (!this.updateById(existing)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "保存工作日志失败");
            }
            return existing.getId();
        }
        WorkLogEntry entry = WorkLogEntry.builder()
                .userId(userId)
                .workDate(request.getWorkDate())
                .createdTime(now)
                .updatedTime(now)
                .build();
        applyFields(entry, request);
        if (!this.save(entry)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "保存工作日志失败");
        }
        return entry.getId();
    }

    @Override
    public WorkLogEntryVO getByDate(LocalDate date, Long userId) {
        if (date == null || userId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        WorkLogEntry entry = findActive(userId, date);
        return entry == null ? null : toVO(entry);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteByDate(LocalDate date, Long userId) {
        if (date == null || userId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        WorkLogEntry entry = findActive(userId, date);
        if (entry == null) {
            return true;
        }
        LocalDateTime now = LocalDateTime.now();
        entry.setDeletedTime(now);
        entry.setUpdatedTime(now);
        return this.updateById(entry);
    }

    @Override
    public List<WorkLogEntryVO> listRecent(Long userId, int limit) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        int size = Math.max(1, Math.min(limit <= 0 ? 200 : limit, 500));
        List<WorkLogEntry> list = this.list(QueryWrapper.create()
                .eq("user_id", userId)
                .isNull("deleted_time")
                .orderBy("work_date", false)
                .limit(size));
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public List<LocalDate> listDatesInMonth(int year, int month, Long userId) {
        if (userId == null || month < 1 || month > 12) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        List<WorkLogEntry> list = this.list(QueryWrapper.create()
                .eq("user_id", userId)
                .isNull("deleted_time")
                .between("work_date", start, end)
                .orderBy("work_date", true));
        return list.stream().map(WorkLogEntry::getWorkDate).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkLogImportResultVO importEntries(WorkLogImportRequest request, Long userId) {
        if (request == null || userId == null || request.getEntries() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean overwrite = request.getOverwrite() == null || request.getOverwrite();
        int imported = 0;
        int skipped = 0;
        for (WorkLogSaveRequest item : request.getEntries()) {
            if (item == null || item.getWorkDate() == null) {
                skipped++;
                continue;
            }
            WorkLogEntry existing = findActive(userId, item.getWorkDate());
            if (existing != null && !overwrite) {
                skipped++;
                continue;
            }
            saveEntry(item, userId);
            imported++;
        }
        WorkLogImportResultVO vo = new WorkLogImportResultVO();
        vo.setImported(imported);
        vo.setSkipped(skipped);
        return vo;
    }

    private WorkLogEntry findActive(Long userId, LocalDate date) {
        return this.getOne(QueryWrapper.create()
                .eq("user_id", userId)
                .eq("work_date", date)
                .isNull("deleted_time")
                .limit(1));
    }

    private void applyFields(WorkLogEntry entry, WorkLogSaveRequest request) {
        String title = StrUtil.trim(request.getTitle());
        entry.setTitle(StrUtil.blankToDefault(title, "工作日志 · " + request.getWorkDate()));
        entry.setDoneText(nullToEmpty(request.getDone()));
        entry.setProblemText(nullToEmpty(request.getProblem()));
        entry.setSummaryText(nullToEmpty(request.getSummary()));
        entry.setPlanText(nullToEmpty(request.getPlan()));
        entry.setTags(serializeTags(request.getTags()));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private String serializeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return JSONUtil.toJsonStr(List.of("worklog"));
        }
        List<String> cleaned = new ArrayList<>();
        for (String t : tags) {
            if (StrUtil.isBlank(t)) continue;
            String v = t.trim().replaceFirst("^#", "");
            if (!cleaned.contains(v)) cleaned.add(v);
        }
        if (!cleaned.contains("worklog")) {
            cleaned.add(0, "worklog");
        }
        return JSONUtil.toJsonStr(cleaned);
    }

    @SuppressWarnings("unchecked")
    private List<String> parseTags(String json) {
        if (StrUtil.isBlank(json)) {
            return List.of("worklog");
        }
        try {
            List<String> list = JSONUtil.toList(json, String.class);
            return list == null || list.isEmpty() ? List.of("worklog") : list;
        } catch (Exception e) {
            return List.of("worklog");
        }
    }

    private WorkLogEntryVO toVO(WorkLogEntry e) {
        WorkLogEntryVO vo = new WorkLogEntryVO();
        vo.setId(e.getId());
        vo.setUserId(e.getUserId());
        vo.setWorkDate(e.getWorkDate());
        vo.setDate(e.getWorkDate() == null ? null : e.getWorkDate().toString());
        vo.setTitle(e.getTitle());
        vo.setDone(nullToEmpty(e.getDoneText()));
        vo.setProblem(nullToEmpty(e.getProblemText()));
        vo.setSummary(nullToEmpty(e.getSummaryText()));
        vo.setPlan(nullToEmpty(e.getPlanText()));
        vo.setTags(parseTags(e.getTags()));
        vo.setCreatedTime(e.getCreatedTime());
        vo.setUpdatedTime(e.getUpdatedTime());
        ZoneId zone = ZoneId.systemDefault();
        if (e.getCreatedTime() != null) {
            vo.setCreatedAt(e.getCreatedTime().atZone(zone).toInstant().toEpochMilli());
        }
        if (e.getUpdatedTime() != null) {
            vo.setUpdatedAt(e.getUpdatedTime().atZone(zone).toInstant().toEpochMilli());
        }
        return vo;
    }
}
