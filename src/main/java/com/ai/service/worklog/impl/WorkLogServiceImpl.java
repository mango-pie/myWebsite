package com.ai.service.worklog.impl;

import com.ai.config.ConditionalOnModule;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.mapper.worklog.WorkLogMapper;
import com.ai.model.dto.worklog.WorkLogImportRequest;
import com.ai.model.dto.worklog.WorkLogSaveRequest;
import com.ai.model.entity.worklog.WorkLog;
import com.ai.model.vo.worklog.WorkLogEntryVO;
import com.ai.model.vo.worklog.WorkLogImportResultVO;
import com.ai.service.worklog.WorkLogService;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@ConditionalOnModule("worklog")
@Service
public class WorkLogServiceImpl implements WorkLogService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int MAX_LIST_LIMIT = 1000;
    private static final int MAX_IMPORT_ENTRIES = 5000;

    @Resource
    private WorkLogMapper workLogMapper;

    @Override
    public WorkLogEntryVO save(WorkLogSaveRequest request, Long userId) {
        LocalDate workDate = parseDate(request == null ? null : request.getWorkDate());
        LocalDateTime now = LocalDateTime.now();
        WorkLog existing = findByDate(userId, workDate);
        WorkLog entity = existing == null ? new WorkLog() : existing;
        if (existing == null) {
            entity.setUserId(userId);
            entity.setWorkDate(workDate);
            entity.setCreateTime(now);
        }
        entity.setTitle(request.getTitle());
        entity.setDone(request.getDone());
        entity.setProblem(request.getProblem());
        entity.setSummary(request.getSummary());
        entity.setPlan(request.getPlan());
        entity.setTags(toTagsJson(request.getTags()));
        entity.setUpdateTime(now);
        if (existing == null) {
            workLogMapper.insert(entity);
        } else {
            workLogMapper.update(entity);
        }
        return toVo(entity);
    }

    @Override
    public List<WorkLogEntryVO> list(Integer limit, Long userId) {
        int safeLimit = limit == null ? 100 : Math.min(MAX_LIST_LIMIT, Math.max(1, limit));
        List<WorkLog> rows = workLogMapper.selectListByQuery(QueryWrapper.create()
                .eq("user_id", userId)
                .orderBy("work_date", false)
                .limit(safeLimit));
        List<WorkLogEntryVO> result = new ArrayList<>(rows.size());
        for (WorkLog row : rows) {
            result.add(toVo(row));
        }
        return result;
    }

    @Override
    public WorkLogEntryVO byDate(String date, Long userId) {
        WorkLog entity = findByDate(userId, parseDate(date));
        return entity == null ? null : toVo(entity);
    }

    @Override
    public boolean delete(String date, Long userId) {
        LocalDate workDate = parseDate(date);
        return workLogMapper.deleteByQuery(QueryWrapper.create()
                .eq("user_id", userId)
                .eq("work_date", workDate)) > 0;
    }

    @Override
    public WorkLogImportResultVO importEntries(WorkLogImportRequest request, Long userId) {
        WorkLogImportResultVO result = new WorkLogImportResultVO();
        if (request == null || request.getEntries() == null || request.getEntries().isEmpty()) {
            return result;
        }
        if (request.getEntries().size() > MAX_IMPORT_ENTRIES) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "单次导入条数超出限制");
        }
        boolean overwrite = Boolean.TRUE.equals(request.getOverwrite());
        for (WorkLogSaveRequest entry : request.getEntries()) {
            LocalDate workDate;
            try {
                workDate = parseDate(entry == null ? null : entry.getWorkDate());
            } catch (BusinessException e) {
                result.setSkipped(result.getSkipped() + 1);
                continue;
            }
            WorkLog existing = findByDate(userId, workDate);
            if (existing != null && !overwrite) {
                result.setSkipped(result.getSkipped() + 1);
                continue;
            }
            WorkLogSaveRequest single = new WorkLogSaveRequest();
            single.setWorkDate(entry.getWorkDate());
            single.setTitle(entry.getTitle());
            single.setDone(entry.getDone());
            single.setProblem(entry.getProblem());
            single.setSummary(entry.getSummary());
            single.setPlan(entry.getPlan());
            single.setTags(entry.getTags());
            save(single, userId);
            result.setImported(result.getImported() + 1);
        }
        return result;
    }

    private WorkLog findByDate(Long userId, LocalDate workDate) {
        return workLogMapper.selectOneByQuery(QueryWrapper.create()
                .eq("user_id", userId)
                .eq("work_date", workDate));
    }

    private LocalDate parseDate(String date) {
        if (StrUtil.isBlank(date)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "workDate 不能为空");
        }
        try {
            return LocalDate.parse(date.trim(), DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "workDate 格式应为 yyyy-MM-dd");
        }
    }

    private String toTagsJson(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        return JSONUtil.toJsonStr(tags);
    }

    private List<String> fromTagsJson(String tagsJson) {
        if (StrUtil.isBlank(tagsJson)) {
            return new ArrayList<>();
        }
        try {
            return JSONUtil.toList(tagsJson, String.class);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private WorkLogEntryVO toVo(WorkLog entity) {
        WorkLogEntryVO vo = new WorkLogEntryVO();
        vo.setId(entity.getId());
        vo.setWorkDate(entity.getWorkDate().format(DATE_FORMAT));
        vo.setTitle(entity.getTitle());
        vo.setDone(entity.getDone());
        vo.setProblem(entity.getProblem());
        vo.setSummary(entity.getSummary());
        vo.setPlan(entity.getPlan());
        vo.setTags(fromTagsJson(entity.getTags()));
        if (entity.getCreateTime() != null) {
            vo.setCreatedAt(entity.getCreateTime().format(TIME_FORMAT));
        }
        if (entity.getUpdateTime() != null) {
            vo.setUpdatedAt(entity.getUpdateTime().format(TIME_FORMAT));
        }
        return vo;
    }
}
