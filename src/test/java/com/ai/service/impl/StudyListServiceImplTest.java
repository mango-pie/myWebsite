package com.ai.service.impl;

import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.mapper.study.StudyListMapper;
import com.ai.model.dto.study.StudyListSortRequest;
import com.ai.model.entity.StudyList;
import com.ai.service.StudyRedisCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * study 清单排序批量化的 service 层单测：mock mapper 与缓存服务，
 * 校验单条批量语句的内容、越权/不存在的报错路径与缓存失效时机。
 */
class StudyListServiceImplTest {

    private static final Long USER_ID = 1L;

    private StudyListServiceImpl listService;
    private StudyListMapper listMapper;
    private StudyRedisCacheService redisCacheService;

    @BeforeEach
    void setUp() {
        listMapper = mock(StudyListMapper.class);
        redisCacheService = mock(StudyRedisCacheService.class);
        listService = new StudyListServiceImpl();
        ReflectionTestUtils.setField(listService, "studyListMapper", listMapper);
        ReflectionTestUtils.setField(listService, "studyRedisCacheService", redisCacheService);
        // ServiceImpl 基类的 mapper 字段：this.list 走它
        ReflectionTestUtils.setField(listService, "mapper", listMapper);
    }

    // ---------- 批量排序 ----------

    @Test
    void sortLists_updatesAllSortOrderInOneStatement() {
        when(listMapper.selectListByQuery(any())).thenReturn(List.of(
                listRow(1L, 3), listRow(2L, 4)));

        StudyListSortRequest request = new StudyListSortRequest();
        request.setItems(List.of(sortItem(1L, 30), sortItem(2L, null), sortItem(2L, 20)));

        assertTrue(listService.sortLists(request, USER_ID));

        ArgumentCaptor<Map<Long, Integer>> sortCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<LocalDateTime> timeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(listMapper).updateSortOrderBatch(eq(USER_ID), sortCaptor.capture(), timeCaptor.capture());
        // (2, null) 按原值兜底，其后 (2, 20) 覆盖为最终值
        assertEquals(Map.of(1L, 30, 2L, 20), sortCaptor.getValue());
        assertNotNull(timeCaptor.getValue());
        // 不再逐条 updateById
        verify(listMapper, never()).update(any(StudyList.class), anyBoolean());
        verify(redisCacheService).evictLists(USER_ID);
    }

    @Test
    void sortLists_resolvesNullAndDuplicateSortItems() {
        when(listMapper.selectListByQuery(any())).thenReturn(List.of(listRow(1L, 3)));

        StudyListSortRequest first = new StudyListSortRequest();
        first.setItems(List.of(sortItem(1L, 5), sortItem(1L, null)));
        StudyListSortRequest second = new StudyListSortRequest();
        second.setItems(List.of(sortItem(1L, null), sortItem(1L, 7)));

        assertTrue(listService.sortLists(first, USER_ID));
        assertTrue(listService.sortLists(second, USER_ID));

        ArgumentCaptor<Map<Long, Integer>> sortCaptor = ArgumentCaptor.forClass(Map.class);
        verify(listMapper, times(2)).updateSortOrderBatch(eq(USER_ID), sortCaptor.capture(), any());
        // 后一个非空值覆盖前值；null 只在不覆盖已有非空值时兜底为当前值
        assertEquals(Map.of(1L, 5), sortCaptor.getAllValues().get(0));
        assertEquals(Map.of(1L, 7), sortCaptor.getAllValues().get(1));
        verify(redisCacheService, times(2)).evictLists(USER_ID);
    }

    // ---------- 排序越权/不存在 ----------

    @Test
    void sortLists_rejectsForeignList() {
        // IN 查询只取回本人的清单 1，清单 999 无权访问
        when(listMapper.selectListByQuery(any())).thenReturn(List.of(listRow(1L, 3)));

        StudyListSortRequest request = new StudyListSortRequest();
        request.setItems(List.of(sortItem(1L, 10), sortItem(999L, 20)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> listService.sortLists(request, USER_ID));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("清单不存在或无权访问"));
        verify(listMapper, never()).updateSortOrderBatch(any(), any(), any());
        verify(redisCacheService, never()).evictLists(USER_ID);
    }

    @Test
    void sortLists_rejectsInvalidListId() {
        when(listMapper.selectListByQuery(any())).thenReturn(List.of());

        StudyListSortRequest request = new StudyListSortRequest();
        request.setItems(List.of(sortItem(null, 10)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> listService.sortLists(request, USER_ID));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
        verify(listMapper, never()).updateSortOrderBatch(any(), any(), any());
        verify(redisCacheService, never()).evictLists(USER_ID);
    }

    // ---------- 造数工具 ----------

    private static StudyListSortRequest.SortItem sortItem(Long id, Integer sortOrder) {
        StudyListSortRequest.SortItem item = new StudyListSortRequest.SortItem();
        item.setId(id);
        item.setSortOrder(sortOrder);
        return item;
    }

    private static StudyList listRow(Long id, Integer sortOrder) {
        return StudyList.builder()
                .id(id)
                .userId(USER_ID)
                .name("清单" + id)
                .sortOrder(sortOrder)
                .taskCount(0)
                .build();
    }
}
