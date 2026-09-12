package com.ai.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.mapper.StudyTaskChecklistMapper;
import com.ai.model.dto.study.StudyChecklistAddRequest;
import com.ai.model.dto.study.StudyChecklistUpdateRequest;
import com.ai.model.entity.StudyTask;
import com.ai.model.entity.StudyTaskChecklist;
import com.ai.model.vo.study.StudyTaskChecklistVO;
import com.ai.service.StudyTaskChecklistService;
import com.ai.service.StudyTaskService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StudyTaskChecklistServiceImpl extends ServiceImpl<StudyTaskChecklistMapper, StudyTaskChecklist>
        implements StudyTaskChecklistService {

    @Lazy
    @Resource
    private StudyTaskService studyTaskService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long addChecklist(StudyChecklistAddRequest request, Long userId) {
        if (request == null || request.getTaskId() == null || StrUtil.isBlank(request.getTitle())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        studyTaskService.getOwnedTask(request.getTaskId(), userId);

        StudyTaskChecklist item = new StudyTaskChecklist();
        item.setTaskId(request.getTaskId());
        item.setUserId(userId);
        item.setTitle(request.getTitle().trim());
        item.setDone(0);
        item.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        item.setCreatedTime(LocalDateTime.now());
        item.setUpdatedTime(LocalDateTime.now());
        if (!this.save(item)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "创建检查项失败");
        }
        return item.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateChecklist(StudyChecklistUpdateRequest request, Long userId) {
        StudyTaskChecklist old = getOwnedChecklist(request.getId(), userId);
        StudyTaskChecklist update = new StudyTaskChecklist();
        update.setId(old.getId());
        if (request.getTitle() != null) {
            update.setTitle(request.getTitle().trim());
        }
        if (request.getDone() != null) {
            update.setDone(request.getDone());
        }
        if (request.getSortOrder() != null) {
            update.setSortOrder(request.getSortOrder());
        }
        update.setUpdatedTime(LocalDateTime.now());
        return this.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteChecklist(Long id, Long userId) {
        getOwnedChecklist(id, userId);
        StudyTaskChecklist update = new StudyTaskChecklist();
        update.setId(id);
        update.setDeletedTime(LocalDateTime.now());
        update.setUpdatedTime(LocalDateTime.now());
        return this.updateById(update);
    }

    @Override
    public List<StudyTaskChecklistVO> listByTaskId(Long taskId, Long userId) {
        studyTaskService.getOwnedTask(taskId, userId);
        List<StudyTaskChecklist> items = this.list(QueryWrapper.create()
                .where("task_id = ?", taskId)
                .and("deleted_time IS NULL")
                .orderBy("sort_order", true)
                .orderBy("id", true));
        return items.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void softDeleteByTaskId(Long taskId) {
        List<StudyTaskChecklist> items = this.list(QueryWrapper.create()
                .where("task_id = ?", taskId)
                .and("deleted_time IS NULL"));
        LocalDateTime now = LocalDateTime.now();
        for (StudyTaskChecklist item : items) {
            StudyTaskChecklist update = new StudyTaskChecklist();
            update.setId(item.getId());
            update.setDeletedTime(now);
            update.setUpdatedTime(now);
            this.updateById(update);
        }
    }

    private StudyTaskChecklist getOwnedChecklist(Long id, Long userId) {
        StudyTaskChecklist item = this.getOne(QueryWrapper.create()
                .where("id = ?", id)
                .and("user_id = ?", userId)
                .and("deleted_time IS NULL"));
        if (item == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "检查项不存在");
        }
        return item;
    }

    private StudyTaskChecklistVO toVO(StudyTaskChecklist item) {
        StudyTaskChecklistVO vo = new StudyTaskChecklistVO();
        BeanUtil.copyProperties(item, vo);
        return vo;
    }
}
