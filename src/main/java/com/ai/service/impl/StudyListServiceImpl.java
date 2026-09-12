package com.ai.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.ai.constant.StudyConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.mapper.StudyListMapper;
import com.ai.mapper.StudyTaskMapper;
import com.ai.model.dto.study.StudyListAddRequest;
import com.ai.model.dto.study.StudyListSortRequest;
import com.ai.model.dto.study.StudyListUpdateRequest;
import com.ai.model.entity.StudyList;
import com.ai.model.entity.StudyTask;
import com.ai.model.vo.study.StudyListVO;
import com.ai.service.StudyListService;
import com.ai.service.StudyRedisCacheService;
import com.ai.utils.StudyDateUtils;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StudyListServiceImpl extends ServiceImpl<StudyListMapper, StudyList> implements StudyListService {

    @Resource
    private StudyTaskMapper studyTaskMapper;

    @Resource
    private StudyRedisCacheService studyRedisCacheService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long addList(StudyListAddRequest request, Long userId) {
        validateName(request.getName());
        checkNameDuplicate(userId, request.getName(), null);

        StudyList list = new StudyList();
        list.setUserId(userId);
        list.setName(request.getName().trim());
        list.setColor(request.getColor());
        list.setIcon(request.getIcon());
        list.setListType(StudyConstant.LIST_TYPE_NORMAL);
        list.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        list.setTaskCount(0);
        list.setStatus(1);
        list.setCreatedTime(LocalDateTime.now());
        list.setUpdatedTime(LocalDateTime.now());

        if (!this.save(list)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "创建清单失败");
        }
        studyRedisCacheService.evictLists(userId);
        return list.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateList(StudyListUpdateRequest request, Long userId) {
        StudyList old = getOwnedList(request.getId(), userId);
        if (StrUtil.isNotBlank(request.getName()) && !request.getName().equals(old.getName())) {
            validateName(request.getName());
            checkNameDuplicate(userId, request.getName(), old.getId());
        }

        StudyList update = new StudyList();
        update.setId(old.getId());
        if (request.getName() != null) {
            update.setName(request.getName().trim());
        }
        if (request.getColor() != null) {
            update.setColor(request.getColor());
        }
        if (request.getIcon() != null) {
            update.setIcon(request.getIcon());
        }
        if (request.getSortOrder() != null) {
            update.setSortOrder(request.getSortOrder());
        }
        if (request.getStatus() != null) {
            update.setStatus(request.getStatus());
        }
        update.setUpdatedTime(LocalDateTime.now());

        if (!this.updateById(update)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "更新清单失败");
        }
        studyRedisCacheService.evictLists(userId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteList(Long id, Long userId) {
        StudyList list = getOwnedList(id, userId);
        if (StudyConstant.LIST_TYPE_INBOX == list.getListType()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "收集箱不可删除");
        }

        StudyList inbox = getOrCreateInbox(userId);
        List<StudyTask> tasks = studyTaskMapper.selectListByQuery(QueryWrapper.create()
                .where("list_id = ?", id)
                .and("user_id = ?", userId)
                .and("deleted_time IS NULL"));

        int pendingCount = 0;
        for (StudyTask task : tasks) {
            StudyTask taskUpdate = new StudyTask();
            taskUpdate.setId(task.getId());
            taskUpdate.setListId(inbox.getId());
            taskUpdate.setUpdatedTime(LocalDateTime.now());
            studyTaskMapper.update(taskUpdate);
            if (StudyConstant.TASK_STATUS_PENDING == task.getStatus()) {
                pendingCount++;
            }
        }
        if (pendingCount > 0) {
            adjustTaskCount(inbox.getId(), pendingCount);
            adjustTaskCount(id, -pendingCount);
        }

        StudyList delete = new StudyList();
        delete.setId(id);
        delete.setDeletedTime(LocalDateTime.now());
        delete.setUpdatedTime(LocalDateTime.now());
        if (!this.updateById(delete)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "删除清单失败");
        }
        studyRedisCacheService.evictLists(userId);
        studyRedisCacheService.evictTodayStats(userId);
        return true;
    }

    @Override
    public List<StudyListVO> getAllLists(Long userId) {
        return studyRedisCacheService.getLists(userId, () -> loadListsFromDb(userId));
    }

    private List<StudyListVO> loadListsFromDb(Long userId) {
        List<StudyList> lists = this.list(QueryWrapper.create()
                .where("user_id = ?", userId)
                .and("deleted_time IS NULL")
                .orderBy("sort_order", true)
                .orderBy("id", true));
        return lists.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean sortLists(StudyListSortRequest request, Long userId) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        for (StudyListSortRequest.SortItem item : request.getItems()) {
            getOwnedList(item.getId(), userId);
            StudyList update = new StudyList();
            update.setId(item.getId());
            update.setSortOrder(item.getSortOrder());
            update.setUpdatedTime(LocalDateTime.now());
            this.updateById(update);
        }
        studyRedisCacheService.evictLists(userId);
        return true;
    }

    @Override
    public StudyList getInboxList(Long userId) {
        StudyList inbox = this.getOne(QueryWrapper.create()
                .where("user_id = ?", userId)
                .and("list_type = ?", StudyConstant.LIST_TYPE_INBOX)
                .and("deleted_time IS NULL"));
        return inbox;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StudyList getOrCreateInbox(Long userId) {
        StudyList inbox = getInboxList(userId);
        if (inbox != null) {
            return inbox;
        }
        inbox = new StudyList();
        inbox.setUserId(userId);
        inbox.setName(StudyConstant.INBOX_NAME);
        inbox.setListType(StudyConstant.LIST_TYPE_INBOX);
        inbox.setSortOrder(0);
        inbox.setTaskCount(0);
        inbox.setStatus(1);
        inbox.setCreatedTime(LocalDateTime.now());
        inbox.setUpdatedTime(LocalDateTime.now());
        this.save(inbox);
        studyRedisCacheService.evictLists(userId);
        return inbox;
    }

    @Override
    public void adjustTaskCount(Long listId, int delta) {
        if (listId == null || delta == 0) {
            return;
        }
        StudyList list = this.getById(listId);
        if (list == null) {
            return;
        }
        int count = list.getTaskCount() != null ? list.getTaskCount() : 0;
        count = Math.max(0, count + delta);
        StudyList update = new StudyList();
        update.setId(listId);
        update.setTaskCount(count);
        update.setUpdatedTime(LocalDateTime.now());
        this.updateById(update);
    }

    @Override
    public StudyList getOwnedList(Long listId, Long userId) {
        if (listId == null || listId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        StudyList list = this.getOne(QueryWrapper.create()
                .where("id = ?", listId)
                .and("user_id = ?", userId)
                .and("deleted_time IS NULL"));
        if (list == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "清单不存在或无权访问");
        }
        return list;
    }

    @Transactional(rollbackFor = Exception.class)
    public void initDefaultLists(Long userId, boolean createThemeLists) {
        getOrCreateInbox(userId);
        if (!createThemeLists) {
            return;
        }
        String[][] themes = {
                {"阅读", "#60a5fa", "book"},
                {"编程", "#34d399", "code"},
                {"复习", "#fbbf24", "read"}
        };
        int order = 1;
        for (String[] theme : themes) {
            long exists = this.count(QueryWrapper.create()
                    .where("user_id = ?", userId)
                    .and("name = ?", theme[0])
                    .and("deleted_time IS NULL"));
            if (exists > 0) {
                continue;
            }
            StudyList list = new StudyList();
            list.setUserId(userId);
            list.setName(theme[0]);
            list.setColor(theme[1]);
            list.setIcon(theme[2]);
            list.setListType(StudyConstant.LIST_TYPE_NORMAL);
            list.setSortOrder(order++);
            list.setTaskCount(0);
            list.setStatus(1);
            list.setCreatedTime(LocalDateTime.now());
            list.setUpdatedTime(LocalDateTime.now());
            this.save(list);
        }
        studyRedisCacheService.evictLists(userId);
    }

    private StudyListVO toVO(StudyList list) {
        StudyListVO vo = new StudyListVO();
        BeanUtil.copyProperties(list, vo);
        vo.setCreatedTime(StudyDateUtils.formatDateTime(list.getCreatedTime()));
        vo.setUpdatedTime(StudyDateUtils.formatDateTime(list.getUpdatedTime()));
        return vo;
    }

    private void validateName(String name) {
        if (StrUtil.isBlank(name) || name.trim().length() > 50) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "清单名称长度应为1-50字");
        }
    }

    private void checkNameDuplicate(Long userId, String name, Long excludeId) {
        QueryWrapper qw = QueryWrapper.create()
                .where("user_id = ?", userId)
                .and("name = ?", name.trim())
                .and("deleted_time IS NULL");
        if (excludeId != null) {
            qw.and("id <> ?", excludeId);
        }
        if (this.count(qw) > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "清单名称已存在");
        }
    }
}
