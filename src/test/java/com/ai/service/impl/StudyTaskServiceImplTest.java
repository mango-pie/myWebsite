package com.ai.service.impl;

import com.ai.constant.StudyConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.mapper.study.StudyTaskMapper;
import com.ai.model.dto.study.StudyTaskMoveRequest;
import com.ai.model.dto.study.StudyTaskSortRequest;
import com.ai.model.entity.StudyList;
import com.ai.model.entity.StudyTask;
import com.ai.service.StudyListService;
import com.ai.service.StudyRedisCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * study 任务排序/移动批量化的 service 层单测：mock mapper 与关联 service，
 * 校验单条批量语句的内容、越权/不存在的报错路径，以及循环外一次计数。
 */
class StudyTaskServiceImplTest {

    private static final Long USER_ID = 1L;
    private static final Long LIST_ID = 10L;
    private static final Long TARGET_LIST_ID = 20L;
    private static final Long SOURCE_A_ID = 30L;
    private static final Long SOURCE_B_ID = 40L;

    private StudyTaskServiceImpl taskService;
    private StudyTaskMapper taskMapper;
    private StudyListService listService;
    private StudyRedisCacheService redisCacheService;

    @BeforeEach
    void setUp() {
        taskMapper = mock(StudyTaskMapper.class);
        listService = mock(StudyListService.class);
        redisCacheService = mock(StudyRedisCacheService.class);
        taskService = new StudyTaskServiceImpl();
        ReflectionTestUtils.setField(taskService, "studyTaskMapper", taskMapper);
        ReflectionTestUtils.setField(taskService, "studyListService", listService);
        ReflectionTestUtils.setField(taskService, "studyRedisCacheService", redisCacheService);
        // ServiceImpl 基类的 mapper 字段：this.list / this.update 走它
        ReflectionTestUtils.setField(taskService, "mapper", taskMapper);
    }

    // ---------- 批量排序 ----------

    @Test
    void sortTasks_updatesAllSortOrderInOneStatement() {
        when(listService.getOwnedList(LIST_ID, USER_ID)).thenReturn(listRow(LIST_ID));
        when(taskMapper.selectListByQuery(any())).thenReturn(List.of(
                task(1L, LIST_ID, StudyConstant.TASK_STATUS_PENDING, 3),
                task(2L, LIST_ID, StudyConstant.TASK_STATUS_DONE, 4)));

        StudyTaskSortRequest request = new StudyTaskSortRequest();
        request.setListId(LIST_ID);
        request.setItems(List.of(sortItem(1L, 30), sortItem(2L, null), sortItem(2L, 10)));

        assertTrue(taskService.sortTasks(request, USER_ID));

        ArgumentCaptor<Map<Long, Integer>> sortCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<LocalDateTime> timeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(taskMapper).updateSortOrderBatch(eq(USER_ID), sortCaptor.capture(), timeCaptor.capture());
        // (2, null) 按原值兜底，其后 (2, 10) 覆盖为最终值
        assertEquals(Map.of(1L, 30, 2L, 10), sortCaptor.getValue());
        assertNotNull(timeCaptor.getValue());
        // 不再逐条 updateById
        verify(taskMapper, never()).update(any(StudyTask.class), anyBoolean());
    }

    @Test
    void sortTasks_lastNonNullSortOrderWinsOnDuplicateIds() {
        when(listService.getOwnedList(LIST_ID, USER_ID)).thenReturn(listRow(LIST_ID));
        when(taskMapper.selectListByQuery(any())).thenReturn(List.of(
                task(1L, LIST_ID, StudyConstant.TASK_STATUS_PENDING, 3)));

        StudyTaskSortRequest first = new StudyTaskSortRequest();
        first.setListId(LIST_ID);
        first.setItems(List.of(sortItem(1L, 5), sortItem(1L, null)));
        StudyTaskSortRequest second = new StudyTaskSortRequest();
        second.setListId(LIST_ID);
        second.setItems(List.of(sortItem(1L, null), sortItem(1L, 7)));

        assertTrue(taskService.sortTasks(first, USER_ID));
        assertTrue(taskService.sortTasks(second, USER_ID));

        ArgumentCaptor<Map<Long, Integer>> sortCaptor = ArgumentCaptor.forClass(Map.class);
        verify(taskMapper, times(2)).updateSortOrderBatch(eq(USER_ID), sortCaptor.capture(), any());
        // 后一个非空值覆盖前值；null 只在不覆盖已有非空值时兜底为当前值
        assertEquals(Map.of(1L, 5), sortCaptor.getAllValues().get(0));
        assertEquals(Map.of(1L, 7), sortCaptor.getAllValues().get(1));
    }

    // ---------- 排序越权/不存在 ----------

    @Test
    void sortTasks_rejectsTaskFromOtherList() {
        when(listService.getOwnedList(LIST_ID, USER_ID)).thenReturn(listRow(LIST_ID));
        when(taskMapper.selectListByQuery(any())).thenReturn(List.of(
                task(1L, LIST_ID, StudyConstant.TASK_STATUS_PENDING, 1),
                task(2L, SOURCE_A_ID, StudyConstant.TASK_STATUS_PENDING, 2)));

        StudyTaskSortRequest request = new StudyTaskSortRequest();
        request.setListId(LIST_ID);
        request.setItems(List.of(sortItem(1L, 10), sortItem(2L, 20)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> taskService.sortTasks(request, USER_ID));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("任务不属于该清单"));
        verify(taskMapper, never()).updateSortOrderBatch(any(), any(), any());
    }

    @Test
    void sortTasks_rejectsMissingTask() {
        when(listService.getOwnedList(LIST_ID, USER_ID)).thenReturn(listRow(LIST_ID));
        when(taskMapper.selectListByQuery(any())).thenReturn(List.of(
                task(1L, LIST_ID, StudyConstant.TASK_STATUS_PENDING, 1)));

        StudyTaskSortRequest request = new StudyTaskSortRequest();
        request.setListId(LIST_ID);
        request.setItems(List.of(sortItem(1L, 10), sortItem(999L, 20)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> taskService.sortTasks(request, USER_ID));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("任务不存在或无权访问"));
        verify(taskMapper, never()).updateSortOrderBatch(any(), any(), any());
    }

    @Test
    void sortTasks_rejectsInvalidTaskId() {
        when(listService.getOwnedList(LIST_ID, USER_ID)).thenReturn(listRow(LIST_ID));

        StudyTaskSortRequest request = new StudyTaskSortRequest();
        request.setListId(LIST_ID);
        request.setItems(List.of(sortItem(null, 10)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> taskService.sortTasks(request, USER_ID));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
        verify(taskMapper, never()).updateSortOrderBatch(any(), any(), any());
    }

    // ---------- 混合移动计数 ----------

    @Test
    void moveTasks_mixedMoveAdjustsCountsOncePerList() {
        when(listService.getOwnedList(TARGET_LIST_ID, USER_ID)).thenReturn(listRow(TARGET_LIST_ID));
        when(taskMapper.selectListByQuery(any())).thenReturn(List.of(
                task(1L, SOURCE_A_ID, StudyConstant.TASK_STATUS_PENDING, 0),
                task(2L, SOURCE_B_ID, StudyConstant.TASK_STATUS_DONE, 0),
                task(3L, SOURCE_B_ID, StudyConstant.TASK_STATUS_PENDING, 0),
                task(4L, TARGET_LIST_ID, StudyConstant.TASK_STATUS_PENDING, 0)));

        StudyTaskMoveRequest request = new StudyTaskMoveRequest();
        request.setTaskIds(List.of(1L, 2L, 3L, 4L));
        request.setTargetListId(TARGET_LIST_ID);

        assertTrue(taskService.moveTasks(request, USER_ID));

        ArgumentCaptor<StudyTask> patchCaptor = ArgumentCaptor.forClass(StudyTask.class);
        verify(taskMapper, times(1)).updateByQuery(patchCaptor.capture(), any());
        assertEquals(TARGET_LIST_ID, patchCaptor.getValue().getListId());
        assertNotNull(patchCaptor.getValue().getUpdatedTime());

        // 每个来源清单聚合一次、目标清单一次，共 3 次；已在目标的任务与已完成任务不计数
        verify(listService).adjustTaskCount(SOURCE_A_ID, -1);
        verify(listService).adjustTaskCount(SOURCE_B_ID, -1);
        verify(listService).adjustTaskCount(TARGET_LIST_ID, 2);
        verify(listService, times(3)).adjustTaskCount(any(), anyInt());
        verify(redisCacheService).evictLists(USER_ID);
        verify(redisCacheService).evictTodayStats(USER_ID);
    }

    @Test
    void moveTasks_deduplicatesRepeatedTaskIds() {
        when(listService.getOwnedList(TARGET_LIST_ID, USER_ID)).thenReturn(listRow(TARGET_LIST_ID));
        when(taskMapper.selectListByQuery(any())).thenReturn(List.of(
                task(1L, SOURCE_A_ID, StudyConstant.TASK_STATUS_PENDING, 0)));

        StudyTaskMoveRequest request = new StudyTaskMoveRequest();
        request.setTaskIds(Arrays.asList(1L, 1L, 1L));
        request.setTargetListId(TARGET_LIST_ID);

        assertTrue(taskService.moveTasks(request, USER_ID));

        // 重复 id 只移动一次：来源 -1、目标 +1，各一次
        verify(listService).adjustTaskCount(SOURCE_A_ID, -1);
        verify(listService).adjustTaskCount(TARGET_LIST_ID, 1);
        verify(listService, times(2)).adjustTaskCount(any(), anyInt());
        verify(taskMapper, times(1)).updateByQuery(any(StudyTask.class), any());
    }

    @Test
    void moveTasks_allAlreadyInTargetKeepsCountersUntouched() {
        when(listService.getOwnedList(TARGET_LIST_ID, USER_ID)).thenReturn(listRow(TARGET_LIST_ID));
        when(taskMapper.selectListByQuery(any())).thenReturn(List.of(
                task(1L, TARGET_LIST_ID, StudyConstant.TASK_STATUS_PENDING, 0)));

        StudyTaskMoveRequest request = new StudyTaskMoveRequest();
        request.setTaskIds(List.of(1L));
        request.setTargetListId(TARGET_LIST_ID);

        assertTrue(taskService.moveTasks(request, USER_ID));

        verify(taskMapper, never()).updateByQuery(any(StudyTask.class), any());
        verify(listService, never()).adjustTaskCount(any(), anyInt());
        verify(redisCacheService, never()).evictLists(USER_ID);
        // 今日统计缓存仍然失效，与原实现一致
        verify(redisCacheService).evictTodayStats(USER_ID);
    }

    // ---------- 移动越权/不存在 ----------

    @Test
    void moveTasks_rejectsForeignTask() {
        when(listService.getOwnedList(TARGET_LIST_ID, USER_ID)).thenReturn(listRow(TARGET_LIST_ID));
        // IN 查询查不到他人的任务
        when(taskMapper.selectListByQuery(any())).thenReturn(List.of());

        StudyTaskMoveRequest request = new StudyTaskMoveRequest();
        request.setTaskIds(List.of(999L));
        request.setTargetListId(TARGET_LIST_ID);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> taskService.moveTasks(request, USER_ID));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("任务不存在或无权访问"));
        verify(taskMapper, never()).updateByQuery(any(StudyTask.class), any());
        verify(listService, never()).adjustTaskCount(any(), anyInt());
        verify(redisCacheService, never()).evictTodayStats(any());
    }

    @Test
    void moveTasks_rejectsInvalidTaskIdBeforeMissing() {
        when(listService.getOwnedList(TARGET_LIST_ID, USER_ID)).thenReturn(listRow(TARGET_LIST_ID));
        when(taskMapper.selectListByQuery(any())).thenReturn(List.of());

        // 非法 id 先于不存在任务报错，与原逐条 getOwnedTask 的校验顺序一致
        StudyTaskMoveRequest invalidFirst = new StudyTaskMoveRequest();
        invalidFirst.setTaskIds(Arrays.asList(null, 999L));
        invalidFirst.setTargetListId(TARGET_LIST_ID);
        BusinessException invalidEx = assertThrows(BusinessException.class,
                () -> taskService.moveTasks(invalidFirst, USER_ID));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), invalidEx.getCode());

        StudyTaskMoveRequest missingFirst = new StudyTaskMoveRequest();
        missingFirst.setTaskIds(Arrays.asList(999L, null));
        missingFirst.setTargetListId(TARGET_LIST_ID);
        BusinessException missingEx = assertThrows(BusinessException.class,
                () -> taskService.moveTasks(missingFirst, USER_ID));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), missingEx.getCode());
    }

    // ---------- 造数工具 ----------

    private static StudyTaskSortRequest.SortItem sortItem(Long id, Integer sortOrder) {
        StudyTaskSortRequest.SortItem item = new StudyTaskSortRequest.SortItem();
        item.setId(id);
        item.setSortOrder(sortOrder);
        return item;
    }

    private static StudyTask task(Long id, Long listId, Integer status, Integer sortOrder) {
        return StudyTask.builder()
                .id(id)
                .userId(USER_ID)
                .listId(listId)
                .title("任务" + id)
                .status(status)
                .sortOrder(sortOrder)
                .build();
    }

    private static StudyList listRow(Long id) {
        return StudyList.builder()
                .id(id)
                .userId(USER_ID)
                .name("清单" + id)
                .sortOrder(0)
                .taskCount(0)
                .build();
    }
}
