package com.ai.service.impl;

import com.ai.config.ConditionalOnModule;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.ai.constant.StudyConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.mapper.study.StudyTaskMapper;
import com.ai.model.dto.study.*;
import com.ai.model.entity.StudyList;
import com.ai.model.entity.StudyTask;
import com.ai.model.vo.study.StudyBlogSyncVO;
import com.ai.model.vo.study.StudyTaskChecklistVO;
import com.ai.model.vo.study.StudyTaskVO;
import com.ai.service.StudyListService;
import com.ai.service.StudyRedisCacheService;
import com.ai.service.StudyTaskChecklistService;
import com.ai.service.StudyTaskService;
import com.ai.service.study.spi.BlogDraftItem;
import com.ai.service.study.spi.BlogDraftReader;
import com.ai.service.study.support.StudyDateUtils;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@ConditionalOnModule("study")
@Service
public class StudyTaskServiceImpl extends ServiceImpl<StudyTaskMapper, StudyTask> implements StudyTaskService {

    @Resource
    private StudyListService studyListService;

    @Resource
    private StudyTaskMapper studyTaskMapper;

    @Lazy
    @Resource
    private StudyTaskChecklistService studyTaskChecklistService;

    @Resource
    private StudyRedisCacheService studyRedisCacheService;

    @Resource
    private BlogDraftReader blogDraftReader;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long addTask(StudyTaskAddRequest request, Long userId) {
        if (request == null || StrUtil.isBlank(request.getTitle())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Long listId = request.getListId();
        if (listId == null) {
            listId = studyListService.getOrCreateInbox(userId).getId();
        } else {
            studyListService.getOwnedList(listId, userId);
        }

        StudyTask task = new StudyTask();
        task.setUserId(userId);
        task.setListId(listId);
        task.setTitle(request.getTitle().trim());
        task.setContent(request.getContent());
        task.setStatus(StudyConstant.TASK_STATUS_PENDING);
        task.setPriority(request.getPriority() != null ? request.getPriority() : 0);
        task.setDueDate(StudyDateUtils.parseDateTime(request.getDueDate()));
        task.setIsToday(Boolean.TRUE.equals(request.getIsToday()) ? 1 : 0);
        task.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        task.setSourceType(request.getSourceType() != null ? request.getSourceType() : StudyConstant.SOURCE_TYPE_MANUAL);
        task.setSourceId(request.getSourceId());
        task.setCreatedTime(LocalDateTime.now());
        task.setUpdatedTime(LocalDateTime.now());

        if (!this.save(task)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "创建任务失败");
        }
        studyListService.adjustTaskCount(listId, 1);
        studyRedisCacheService.evictTodayStats(userId);
        studyRedisCacheService.evictLists(userId);
        return task.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateTask(StudyTaskUpdateRequest request, Long userId) {
        StudyTask old = getOwnedTask(request.getId(), userId);
        Long newListId = request.getListId();
        if (newListId != null && !newListId.equals(old.getListId())) {
            studyListService.getOwnedList(newListId, userId);
        }

        StudyTask update = new StudyTask();
        update.setId(old.getId());
        if (request.getTitle() != null) {
            update.setTitle(request.getTitle().trim());
        }
        if (request.getListId() != null) {
            update.setListId(request.getListId());
        }
        if (request.getContent() != null) {
            update.setContent(request.getContent());
        }
        if (request.getPriority() != null) {
            update.setPriority(request.getPriority());
        }
        if (request.getDueDate() != null) {
            update.setDueDate("null".equalsIgnoreCase(request.getDueDate())
                    ? null : StudyDateUtils.parseDateTime(request.getDueDate()));
        }
        if (request.getIsToday() != null) {
            update.setIsToday(Boolean.TRUE.equals(request.getIsToday()) ? 1 : 0);
        }
        if (request.getSortOrder() != null) {
            update.setSortOrder(request.getSortOrder());
        }
        update.setUpdatedTime(LocalDateTime.now());

        if (!this.updateById(update)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "更新任务失败");
        }

        if (newListId != null && !newListId.equals(old.getListId())
                && StudyConstant.TASK_STATUS_PENDING == old.getStatus()) {
            studyListService.adjustTaskCount(old.getListId(), -1);
            studyListService.adjustTaskCount(newListId, 1);
            studyRedisCacheService.evictLists(userId);
        }
        studyRedisCacheService.evictTodayStats(userId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleTask(StudyTaskToggleRequest request, Long userId) {
        StudyTask task = getOwnedTask(request.getId(), userId);
        boolean done = Boolean.TRUE.equals(request.getDone());
        int oldStatus = task.getStatus() != null ? task.getStatus() : StudyConstant.TASK_STATUS_PENDING;

        StudyTask update = new StudyTask();
        update.setId(task.getId());
        if (done) {
            update.setStatus(StudyConstant.TASK_STATUS_DONE);
            update.setCompletedTime(LocalDateTime.now());
        } else {
            update.setStatus(StudyConstant.TASK_STATUS_PENDING);
            update.setCompletedTime(null);
        }
        update.setUpdatedTime(LocalDateTime.now());
        this.updateById(update);

        if (done && oldStatus == StudyConstant.TASK_STATUS_PENDING) {
            studyListService.adjustTaskCount(task.getListId(), -1);
            studyRedisCacheService.evictLists(userId);
        } else if (!done && oldStatus == StudyConstant.TASK_STATUS_DONE) {
            studyListService.adjustTaskCount(task.getListId(), 1);
            studyRedisCacheService.evictLists(userId);
        }
        studyRedisCacheService.evictTodayStats(userId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteTask(Long id, Long userId) {
        StudyTask task = getOwnedTask(id, userId);
        StudyTask update = new StudyTask();
        update.setId(id);
        update.setDeletedTime(LocalDateTime.now());
        update.setUpdatedTime(LocalDateTime.now());
        this.updateById(update);

        if (StudyConstant.TASK_STATUS_PENDING == task.getStatus()) {
            studyListService.adjustTaskCount(task.getListId(), -1);
            studyRedisCacheService.evictLists(userId);
        }
        studyTaskChecklistService.softDeleteByTaskId(id);
        studyRedisCacheService.evictTodayStats(userId);
        return true;
    }

    @Override
    public StudyTaskVO getTaskVO(Long id, Long userId) {
        StudyTask task = getOwnedTask(id, userId);
        return toVO(task, true);
    }

    @Override
    public Page<StudyTaskVO> queryTaskView(StudyTaskViewQueryRequest request, Long userId) {
        StudyConstant.StudyView view = StudyConstant.StudyView.fromValue(request.getView());
        if (view == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "无效的视图类型");
        }

        int pageNum = request.getPageNum() != null && request.getPageNum() > 0 ? request.getPageNum() : 1;
        int pageSize = request.getPageSize() != null && request.getPageSize() > 0 ? request.getPageSize() : 50;
        if (pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        QueryWrapper qw = QueryWrapper.create()
                .where("user_id = ?", userId)
                .and("deleted_time IS NULL");

        switch (view) {
            case TODAY -> qw.and("(due_date <= ? OR is_today = 1)", StudyDateUtils.todayEnd())
                    .and("status = ?", StudyConstant.TASK_STATUS_PENDING);
            case WEEK -> qw.and("due_date >= ?", StudyDateUtils.weekStart())
                    .and("due_date <= ?", StudyDateUtils.weekEnd())
                    .and("status = ?", StudyConstant.TASK_STATUS_PENDING);
            case INBOX -> {
                Long inboxId = studyListService.getOrCreateInbox(userId).getId();
                qw.and("list_id = ?", inboxId)
                        .and("status = ?", StudyConstant.TASK_STATUS_PENDING);
            }
            case COMPLETED -> {
                int days = request.getCompletedDays() != null && request.getCompletedDays() > 0
                        ? request.getCompletedDays() : 7;
                qw.and("status = ?", StudyConstant.TASK_STATUS_DONE)
                        .and("completed_time >= ?", LocalDateTime.now().minusDays(days));
            }
            case LIST -> {
                if (request.getListId() == null) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "list 视图需要 listId");
                }
                studyListService.getOwnedList(request.getListId(), userId);
                qw.and("list_id = ?", request.getListId());
                if (Boolean.TRUE.equals(request.getHideCompleted())) {
                    qw.and("status = ?", StudyConstant.TASK_STATUS_PENDING);
                }
            }
        }

        qw.orderBy("status", true)
                .orderBy("priority", false)
                .orderBy("due_date IS NULL", true)
                .orderBy("due_date", true)
                .orderBy("sort_order", true)
                .orderBy("created_time", false);

        Page<StudyTask> taskPage = this.page(Page.of(pageNum, pageSize), qw);
        Page<StudyTaskVO> voPage = new Page<>();
        voPage.setPageNumber(taskPage.getPageNumber());
        voPage.setPageSize(taskPage.getPageSize());
        voPage.setTotalPage(taskPage.getTotalPage());
        voPage.setTotalRow(taskPage.getTotalRow());
        voPage.setRecords(taskPage.getRecords().stream()
                .map(t -> toVO(t, false))
                .collect(Collectors.toList()));
        return voPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean sortTasks(StudyTaskSortRequest request, Long userId) {
        if (request.getListId() == null || CollUtil.isEmpty(request.getItems())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        studyListService.getOwnedList(request.getListId(), userId);

        // 一次 IN 查询批量取回任务，替代循环内逐条 getOwnedTask
        Map<Long, StudyTask> taskMap = listOwnedTasksByIds(userId, request.getItems().stream()
                .map(StudyTaskSortRequest.SortItem::getId)
                .collect(Collectors.toList()));

        // 逐项校验，报错类型与顺序和原逐条 getOwnedTask + 清单归属判断保持一致
        for (StudyTaskSortRequest.SortItem item : request.getItems()) {
            if (item.getId() == null || item.getId() <= 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR);
            }
            StudyTask task = taskMap.get(item.getId());
            if (task == null) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务不存在或无权访问");
            }
            if (!request.getListId().equals(task.getListId())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "任务不属于该清单");
            }
        }

        // 解析每个任务的最终排序值：null 排序值保持当前值（原 updateById 忽略 null 字段的语义），
        // 同一任务多次出现时后一个非空值覆盖前值，按 id 去重后单条 SQL 批量更新
        Map<Long, Integer> sortOrders = new LinkedHashMap<>();
        for (StudyTaskSortRequest.SortItem item : request.getItems()) {
            if (item.getSortOrder() != null) {
                sortOrders.put(item.getId(), item.getSortOrder());
            } else if (!sortOrders.containsKey(item.getId())) {
                sortOrders.put(item.getId(), taskMap.get(item.getId()).getSortOrder());
            }
        }
        studyTaskMapper.updateSortOrderBatch(userId, sortOrders, LocalDateTime.now());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean moveTasks(StudyTaskMoveRequest request, Long userId) {
        if (request.getTargetListId() == null || CollUtil.isEmpty(request.getTaskIds())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        studyListService.getOwnedList(request.getTargetListId(), userId);

        // 一次 IN 查询批量取回任务，替代循环内逐条 getOwnedTask
        Map<Long, StudyTask> taskMap = listOwnedTasksByIds(userId, request.getTaskIds());

        // 逐项校验，报错类型与顺序和原逐条 getOwnedTask 保持一致
        for (Long taskId : request.getTaskIds()) {
            if (taskId == null || taskId <= 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR);
            }
            if (!taskMap.containsKey(taskId)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务不存在或无权访问");
            }
        }

        // 仅移动不在目标清单里的任务；重复 id 只处理一次，与原循环内 continue 跳过语义一致
        List<Long> movedIds = new ArrayList<>();
        Map<Long, Integer> pendingCountByList = new LinkedHashMap<>();
        int pendingMoved = 0;
        for (StudyTask task : taskMap.values()) {
            if (task.getListId().equals(request.getTargetListId())) {
                continue;
            }
            movedIds.add(task.getId());
            if (StudyConstant.TASK_STATUS_PENDING == task.getStatus()) {
                pendingCountByList.merge(task.getListId(), 1, Integer::sum);
                pendingMoved++;
            }
        }

        // 移动结果一条 UPDATE 批量落库
        if (!movedIds.isEmpty()) {
            StudyTask patch = new StudyTask();
            patch.setListId(request.getTargetListId());
            patch.setUpdatedTime(LocalDateTime.now());
            this.update(patch, QueryWrapper.create().in("id", movedIds));
        }

        // 计数在循环外按最终状态一次计算：每个来源清单聚合调整一次，目标清单一次
        // （同为负向增量，一次性 clamp 与原逐次 -1 的 clamp 结果一致）
        for (Map.Entry<Long, Integer> entry : pendingCountByList.entrySet()) {
            studyListService.adjustTaskCount(entry.getKey(), -entry.getValue());
        }
        if (pendingMoved > 0) {
            studyListService.adjustTaskCount(request.getTargetListId(), pendingMoved);
            studyRedisCacheService.evictLists(userId);
        }
        studyRedisCacheService.evictTodayStats(userId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StudyBlogSyncVO syncBlogDrafts(Long userId) {
        StudyList inbox = studyListService.getOrCreateInbox(userId);
        List<BlogDraftItem> drafts = blogDraftReader.listDraftsByUser(userId);

        List<Long> taskIds = new ArrayList<>();
        int synced = 0;
        for (BlogDraftItem post : drafts) {
            StudyTask existing = this.getOne(QueryWrapper.create()
                    .where("user_id = ?", userId)
                    .and("source_type = ?", StudyConstant.SOURCE_TYPE_BLOG_DRAFT)
                    .and("source_id = ?", post.getId())
                    .and("deleted_time IS NULL"));
            if (existing != null) {
                StudyTask update = new StudyTask();
                update.setId(existing.getId());
                update.setTitle("发布《" + post.getTitle() + "》");
                update.setUpdatedTime(LocalDateTime.now());
                this.updateById(update);
                taskIds.add(existing.getId());
            } else {
                StudyTaskAddRequest addRequest = new StudyTaskAddRequest();
                addRequest.setTitle("发布《" + post.getTitle() + "》");
                addRequest.setListId(inbox.getId());
                addRequest.setSourceType(StudyConstant.SOURCE_TYPE_BLOG_DRAFT);
                addRequest.setSourceId(post.getId());
                long id = addTask(addRequest, userId);
                taskIds.add(id);
                synced++;
            }
        }
        StudyBlogSyncVO vo = new StudyBlogSyncVO();
        vo.setSyncedCount(synced);
        vo.setTaskIds(taskIds);
        return vo;
    }

    @Override
    public StudyTask getOwnedTask(Long taskId, Long userId) {
        if (taskId == null || taskId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        StudyTask task = this.getOne(QueryWrapper.create()
                .where("id = ?", taskId)
                .and("user_id = ?", userId)
                .and("deleted_time IS NULL"));
        if (task == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务不存在或无权访问");
        }
        return task;
    }

    /**
     * 按 id 批量取回本人未删除任务（过滤非法 id），查询条件与 getOwnedTask 一致，
     * 供 sortTasks/moveTasks 一次 IN 查询完成归属校验。
     */
    private Map<Long, StudyTask> listOwnedTasksByIds(Long userId, List<Long> taskIds) {
        List<Long> validIds = taskIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .collect(Collectors.toList());
        if (validIds.isEmpty()) {
            return Map.of();
        }
        List<StudyTask> tasks = this.list(QueryWrapper.create()
                .where("user_id = ?", userId)
                .and("deleted_time IS NULL")
                .in("id", validIds));
        return tasks.stream().collect(Collectors.toMap(StudyTask::getId, Function.identity()));
    }

    private StudyTaskVO toVO(StudyTask task, boolean withChecklist) {
        StudyTaskVO vo = new StudyTaskVO();
        BeanUtil.copyProperties(task, vo, "dueDate", "completedTime", "createdTime", "updatedTime");
        vo.setDueDate(StudyDateUtils.formatDateTime(task.getDueDate()));
        vo.setCompletedTime(StudyDateUtils.formatDateTime(task.getCompletedTime()));
        vo.setCreatedTime(StudyDateUtils.formatDateTime(task.getCreatedTime()));
        vo.setUpdatedTime(StudyDateUtils.formatDateTime(task.getUpdatedTime()));

        StudyList list = studyListService.getById(task.getListId());
        if (list != null) {
            vo.setListName(list.getName());
        }
        if (withChecklist) {
            vo.setChecklistItems(studyTaskChecklistService.listByTaskId(task.getId(), task.getUserId()));
        }
        return vo;
    }
}
