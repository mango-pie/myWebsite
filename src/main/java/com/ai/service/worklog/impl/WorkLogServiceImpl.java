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
import com.mybatisflex.core.row.Db;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@ConditionalOnModule("worklog")
@Service
public class WorkLogServiceImpl implements WorkLogService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int MAX_LIST_LIMIT = 1000;
    private static final int MAX_IMPORT_ENTRIES = 5000;
    /** 批量落库的每批条数，避免单条 SQL 过大 */
    private static final int IMPORT_BATCH_SIZE = 1000;

    @Resource
    private WorkLogMapper workLogMapper;

    @Resource
    private TransactionTemplate transactionTemplate;

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
        // 预解析收集涉及日期，一次性查出本用户这些日期的已有记录，消除循环内逐条 findByDate 的 N+1 查询；
        // 与改前一致：解析失败条目不参与查询，主循环中照常计入 skipped
        Set<LocalDate> involvedDates = new LinkedHashSet<>();
        for (WorkLogSaveRequest entry : request.getEntries()) {
            try {
                involvedDates.add(parseDate(entry == null ? null : entry.getWorkDate()));
            } catch (BusinessException ignored) {
                // 无效日期条目在主循环中跳过并计数
            }
        }
        // 日期 → 已有记录的内存索引：upsert 判断全部走该 map，不再逐条查库
        Map<LocalDate, WorkLog> existingMap = findExistingByDates(userId, involvedDates);
        List<WorkLog> toInsert = new ArrayList<>();
        List<WorkLog> toUpdate = new ArrayList<>();
        for (WorkLogSaveRequest entry : request.getEntries()) {
            LocalDate workDate;
            try {
                workDate = parseDate(entry == null ? null : entry.getWorkDate());
            } catch (BusinessException e) {
                result.setSkipped(result.getSkipped() + 1);
                continue;
            }
            WorkLog entity = existingMap.get(workDate);
            if (entity != null && !overwrite) {
                result.setSkipped(result.getSkipped() + 1);
                continue;
            }
            if (entity == null) {
                // 新日期：新建待插入实体并登记进 map，同批同日的后续条目会命中它
                entity = new WorkLog();
                entity.setUserId(userId);
                entity.setWorkDate(workDate);
                entity.setCreateTime(LocalDateTime.now());
                existingMap.put(workDate, entity);
                toInsert.add(entity);
            } else if (entity.getId() != null) {
                // 数据库已有记录：加入待更新列表；同批同日多条会重复加入，
                // 批量按序执行、后写覆盖，与改前逐条 update 的最终状态一致
                toUpdate.add(entity);
            }
            // 命中的是本批待插入实体（同批同日重复条目）时仅覆盖字段值，
            // 随首次 INSERT 一并落库，等价改前“先 insert 后 update”
            LocalDateTime now = LocalDateTime.now();
            entity.setTitle(entry.getTitle());
            entity.setDone(entry.getDone());
            entity.setProblem(entry.getProblem());
            entity.setSummary(entry.getSummary());
            entity.setPlan(entry.getPlan());
            entity.setTags(toTagsJson(entry.getTags()));
            entity.setUpdateTime(now);
            result.setImported(result.getImported() + 1);
        }
        if (!toInsert.isEmpty() || !toUpdate.isEmpty()) {
            // 批量写入包进事务：插入与更新两阶段要么全部生效、要么全部回滚，
            // 避免中途失败（如单条数据超长）留下“已插入未更新”的部分持久化；
            // 回滚后异常继续上抛，仍由全局异常处理器返回错误响应
            transactionTemplate.executeWithoutResult(status -> {
                if (!toInsert.isEmpty()) {
                    workLogMapper.insertBatch(toInsert, IMPORT_BATCH_SIZE);
                }
                if (!toUpdate.isEmpty()) {
                    // Flex 1.11.0 的 BaseMapper 无批量更新接口，用 Db.executeBatch 在 BATCH 会话中
                    // 逐条调用与改前相同的 update(entity)（忽略 null 字段、按 id 更新），仅合并提交
                    Db.executeBatch(toUpdate, IMPORT_BATCH_SIZE, WorkLogMapper.class, WorkLogMapper::update);
                }
            });
        }
        return result;
    }

    /**
     * 一次性查出本用户在给定日期集合下的已有记录，按日期建索引。
     */
    private Map<LocalDate, WorkLog> findExistingByDates(Long userId, Collection<LocalDate> workDates) {
        if (workDates.isEmpty()) {
            return new HashMap<>();
        }
        List<WorkLog> rows = workLogMapper.selectListByQuery(QueryWrapper.create()
                .eq("user_id", userId)
                .in("work_date", workDates));
        Map<LocalDate, WorkLog> existingMap = new HashMap<>();
        for (WorkLog row : rows) {
            existingMap.put(row.getWorkDate(), row);
        }
        return existingMap;
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
