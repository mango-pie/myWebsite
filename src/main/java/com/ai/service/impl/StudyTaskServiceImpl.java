package com.ai.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.ai.constant.StudyConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.mapper.StudyTaskMapper;
import com.ai.mapper.BlogPostMapper;
import com.ai.model.dto.study.*;
import com.ai.model.entity.BlogPost;
import com.ai.model.entity.StudyList;
import com.ai.model.entity.StudyTask;
import com.ai.model.vo.study.StudyBlogSyncVO;
import com.ai.model.vo.study.StudyTaskChecklistVO;
import com.ai.model.vo.study.StudyTaskVO;
import com.ai.service.StudyListService;
import com.ai.service.StudyRedisCacheService;
import com.ai.service.StudyTaskChecklistService;
import com.ai.service.StudyTaskService;
import com.ai.utils.StudyDateUtils;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StudyTaskServiceImpl extends ServiceImpl<StudyTaskMapper, StudyTask> implements StudyTaskService {

    @Resource
    private StudyListService studyListService;

    @Lazy
    @Resource
    private StudyTaskChecklistService studyTaskChecklistService;

    @Resource
    private StudyRedisCacheService studyRedisCacheService;

    @Resource
    private BlogPostMapper blogPostMapper;

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
        for (StudyTaskSortRequest.SortItem item : request.getItems()) {
            StudyTask task = getOwnedTask(item.getId(), userId);
            if (!request.getListId().equals(task.getListId())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "任务不属于该清单");
            }
            StudyTask update = new StudyTask();
            update.setId(item.getId());
            update.setSortOrder(item.getSortOrder());
            update.setUpdatedTime(LocalDateTime.now());
            this.updateById(update);
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean moveTasks(StudyTaskMoveRequest request, Long userId) {
        if (request.getTargetListId() == null || CollUtil.isEmpty(request.getTaskIds())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        studyListService.getOwnedList(request.getTargetListId(), userId);
        int pendingMoved = 0;
        for (Long taskId : request.getTaskIds()) {
            StudyTask task = getOwnedTask(taskId, userId);
            if (task.getListId().equals(request.getTargetListId())) {
                continue;
            }
            if (StudyConstant.TASK_STATUS_PENDING == task.getStatus()) {
                studyListService.adjustTaskCount(task.getListId(), -1);
                pendingMoved++;
            }
            StudyTask update = new StudyTask();
            update.setId(taskId);
            update.setListId(request.getTargetListId());
            update.setUpdatedTime(LocalDateTime.now());
            this.updateById(update);
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
        List<BlogPost> drafts = blogPostMapper.selectListByQuery(QueryWrapper.create()
                .where("user_id = ?", userId)
                .and("status = ?", 0));

        List<Long> taskIds = new ArrayList<>();
        int synced = 0;
        for (BlogPost post : drafts) {
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
