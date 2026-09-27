package com.ai.service.worklog.impl;

import cn.hutool.json.JSONUtil;
import com.ai.exception.BusinessException;
import com.ai.mapper.worklog.WorkLogMapper;
import com.ai.model.dto.worklog.WorkLogImportRequest;
import com.ai.model.dto.worklog.WorkLogSaveRequest;
import com.ai.model.entity.worklog.WorkLog;
import com.ai.model.vo.worklog.WorkLogImportResultVO;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Db;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 工作日志导入消除 N+1 的行为等价性：
 * 预解析日期后仅一次 SELECT 建日期索引，upsert 判断全部在内存完成，落库改为批量；
 * 空导入、全新日期、同日 overwrite / skip、同批同日重复、无效日期与 5000 上限校验语义均与改前一致。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkLogServiceImplTest {

    private static final Long USER_ID = 7L;

    @Mock
    private WorkLogMapper workLogMapper;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private WorkLogServiceImpl service;

    @BeforeEach
    void setUp() {
        // 事务模板直通执行回调，与 LibraryRecycleServiceTest 的处理方式一致
        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    // ---------- 空导入 ----------

    @Test
    void import_nullOrEmptyEntries_returnsEmptyResultWithoutQuery() {
        WorkLogImportResultVO nullResult = service.importEntries(null, USER_ID);
        assertEquals(0, nullResult.getImported());
        assertEquals(0, nullResult.getSkipped());

        WorkLogImportRequest request = new WorkLogImportRequest();
        request.setEntries(List.of());
        WorkLogImportResultVO emptyResult = service.importEntries(request, USER_ID);
        assertEquals(0, emptyResult.getImported());
        assertEquals(0, emptyResult.getSkipped());

        verifyNoInteractions(workLogMapper);
    }

    // ---------- 上限校验 ----------

    @Test
    void import_overLimitEntries_throwsWithoutQuery() {
        WorkLogImportRequest request = new WorkLogImportRequest();
        List<WorkLogSaveRequest> entries = new ArrayList<>();
        for (int i = 0; i <= 5000; i++) {
            entries.add(entry("2026-01-01", "条目" + i, null));
        }
        request.setEntries(entries);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.importEntries(request, USER_ID));

        assertTrue(ex.getMessage().contains("单次导入条数超出限制"));
        verifyNoInteractions(workLogMapper);
    }

    // ---------- 全新日期 ----------

    @Test
    void import_newDates_singleQueryThenBatchInsert() {
        WorkLogImportRequest request = new WorkLogImportRequest();
        request.setOverwrite(true);
        request.setEntries(List.of(
                entry("2026-02-01", "第一天", List.of("a", "b")),
                entry("2026-02-02", "第二天", null)));
        when(workLogMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

        WorkLogImportResultVO vo = service.importEntries(request, USER_ID);

        assertEquals(2, vo.getImported());
        assertEquals(0, vo.getSkipped());
        // N+1 消除：只允许一次批量 SELECT，且不再走逐条 selectOneByQuery / 单条 insert、update
        verify(workLogMapper, times(1)).selectListByQuery(any(QueryWrapper.class));
        verify(workLogMapper, never()).selectOneByQuery(any(QueryWrapper.class));
        verify(workLogMapper, never()).insert(any(WorkLog.class));
        verify(workLogMapper, never()).update(any(WorkLog.class));

        ArgumentCaptor<List<WorkLog>> insertCaptor = ArgumentCaptor.forClass(List.class);
        verify(workLogMapper).insertBatch(insertCaptor.capture(), eq(1000));
        List<WorkLog> inserted = insertCaptor.getValue();
        assertEquals(2, inserted.size());
        WorkLog first = inserted.get(0);
        assertEquals(USER_ID, first.getUserId());
        assertEquals(LocalDate.of(2026, 2, 1), first.getWorkDate());
        assertEquals("第一天", first.getTitle());
        assertEquals(JSONUtil.toJsonStr(List.of("a", "b")), first.getTags());
        assertNotNull(first.getCreateTime());
        assertNotNull(first.getUpdateTime());
        assertNull(inserted.get(1).getTags());
    }

    // ---------- 同日 overwrite ----------

    @Test
    void import_sameDateOverwrite_updatesExistingRowById() {
        WorkLog existing = existingRow(LocalDate.of(2026, 1, 5), 9L);
        WorkLogImportRequest request = new WorkLogImportRequest();
        request.setOverwrite(true);
        request.setEntries(List.of(entry("2026-01-05", "覆盖后的标题", List.of("c", "d"))));
        when(workLogMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(existing));

        try (MockedStatic<Db> db = mockStatic(Db.class)) {
            db.when(() -> Db.executeBatch(anyCollection(), anyInt(), eq(WorkLogMapper.class),
                            ArgumentMatchers.<BiConsumer<WorkLogMapper, WorkLog>>any()))
                    .thenAnswer(invocation -> {
                        @SuppressWarnings("unchecked")
                        Collection<WorkLog> entities = (Collection<WorkLog>) invocation.getArgument(0);
                        @SuppressWarnings("unchecked")
                        BiConsumer<WorkLogMapper, WorkLog> consumer =
                                (BiConsumer<WorkLogMapper, WorkLog>) invocation.getArgument(3);
                        entities.forEach(entity -> consumer.accept(workLogMapper, entity));
                        return new int[entities.size()];
                    });

            WorkLogImportResultVO vo = service.importEntries(request, USER_ID);

            assertEquals(1, vo.getImported());
            assertEquals(0, vo.getSkipped());
        }

        verify(workLogMapper, times(1)).selectListByQuery(any(QueryWrapper.class));
        verify(workLogMapper, never()).selectOneByQuery(any(QueryWrapper.class));
        verify(workLogMapper, never()).insertBatch(anyList(), anyInt());

        ArgumentCaptor<WorkLog> updateCaptor = ArgumentCaptor.forClass(WorkLog.class);
        verify(workLogMapper).update(updateCaptor.capture());
        WorkLog updated = updateCaptor.getValue();
        assertEquals(9L, updated.getId());
        assertEquals("覆盖后的标题", updated.getTitle());
        assertEquals(JSONUtil.toJsonStr(List.of("c", "d")), updated.getTags());
        // 更新沿用库中已加载实体，createTime 保持原值
        assertEquals(LocalDateTime.of(2026, 1, 1, 8, 0), updated.getCreateTime());
        assertNotNull(updated.getUpdateTime());
    }

    // ---------- 同日 skip ----------

    @Test
    void import_sameDateWithoutOverwrite_skipsAndWritesNothing() {
        WorkLog existing = existingRow(LocalDate.of(2026, 1, 5), 11L);
        WorkLogImportRequest request = new WorkLogImportRequest();
        request.setOverwrite(null);
        request.setEntries(List.of(entry("2026-01-05", "不应生效", List.of("x"))));
        when(workLogMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(existing));

        WorkLogImportResultVO vo = service.importEntries(request, USER_ID);

        assertEquals(0, vo.getImported());
        assertEquals(1, vo.getSkipped());
        verify(workLogMapper, times(1)).selectListByQuery(any(QueryWrapper.class));
        verify(workLogMapper, never()).selectOneByQuery(any(QueryWrapper.class));
        verify(workLogMapper, never()).insertBatch(anyList(), anyInt());
        verify(workLogMapper, never()).update(any(WorkLog.class));
        // 没有可写入内容时不开启事务
        verify(transactionTemplate, never()).executeWithoutResult(any());
    }

    // ---------- 同批同日重复 ----------

    @Test
    void import_duplicateNewDateInBatch_mergesIntoSingleInsert() {
        WorkLogImportRequest request = new WorkLogImportRequest();
        request.setOverwrite(true);
        request.setEntries(List.of(
                entry("2026-03-01", "第一条", List.of("a")),
                entry("2026-03-01", "第二条", List.of("b"))));
        when(workLogMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

        WorkLogImportResultVO vo = service.importEntries(request, USER_ID);

        // 改前：第一条 insert、第二条同日 update 覆盖 → imported=2；改后合并为一次 INSERT，最终值相同
        assertEquals(2, vo.getImported());
        assertEquals(0, vo.getSkipped());

        ArgumentCaptor<List<WorkLog>> insertCaptor = ArgumentCaptor.forClass(List.class);
        verify(workLogMapper).insertBatch(insertCaptor.capture(), eq(1000));
        List<WorkLog> inserted = insertCaptor.getValue();
        assertEquals(1, inserted.size());
        assertEquals("第二条", inserted.get(0).getTitle());
        assertEquals(JSONUtil.toJsonStr(List.of("b")), inserted.get(0).getTags());
        verify(workLogMapper, never()).update(any(WorkLog.class));
    }

    @Test
    void import_duplicateNewDateWithoutOverwrite_secondEntrySkipped() {
        WorkLogImportRequest request = new WorkLogImportRequest();
        request.setOverwrite(false);
        request.setEntries(List.of(
                entry("2026-03-01", "第一条", null),
                entry("2026-03-01", "第二条", null)));
        when(workLogMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

        WorkLogImportResultVO vo = service.importEntries(request, USER_ID);

        // 改前：第一条落库后，第二条同日命中已有记录被跳过
        assertEquals(1, vo.getImported());
        assertEquals(1, vo.getSkipped());

        ArgumentCaptor<List<WorkLog>> insertCaptor = ArgumentCaptor.forClass(List.class);
        verify(workLogMapper).insertBatch(insertCaptor.capture(), eq(1000));
        assertEquals(1, insertCaptor.getValue().size());
        assertEquals("第一条", insertCaptor.getValue().get(0).getTitle());
    }

    // ---------- 无效日期 ----------

    @Test
    void import_invalidDateEntry_skippedAndNotUpserted() {
        WorkLogImportRequest request = new WorkLogImportRequest();
        request.setOverwrite(true);
        request.setEntries(List.of(
                entry("2026/01/05", "格式错误", null),
                entry("2026-01-06", "合法条目", null)));
        when(workLogMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

        WorkLogImportResultVO vo = service.importEntries(request, USER_ID);

        assertEquals(1, vo.getImported());
        assertEquals(1, vo.getSkipped());
        verify(workLogMapper, times(1)).selectListByQuery(any(QueryWrapper.class));
        ArgumentCaptor<List<WorkLog>> insertCaptor = ArgumentCaptor.forClass(List.class);
        verify(workLogMapper).insertBatch(insertCaptor.capture(), eq(1000));
        assertEquals(1, insertCaptor.getValue().size());
        assertEquals(LocalDate.of(2026, 1, 6), insertCaptor.getValue().get(0).getWorkDate());
    }

    @Test
    void import_allInvalidDates_skipsAllWithoutAnyQuery() {
        WorkLogImportRequest request = new WorkLogImportRequest();
        request.setEntries(List.of(
                entry("bad", "x", null),
                entry("2026-13-01", "y", null)));

        WorkLogImportResultVO vo = service.importEntries(request, USER_ID);

        assertEquals(0, vo.getImported());
        assertEquals(2, vo.getSkipped());
        verify(workLogMapper, never()).selectListByQuery(any(QueryWrapper.class));
    }

    // ---------- 事务边界 ----------

    @Test
    void import_mixedInsertAndUpdate_singleTransactionWrapsBothPhases() {
        WorkLog existing = existingRow(LocalDate.of(2026, 1, 5), 9L);
        WorkLogImportRequest request = new WorkLogImportRequest();
        request.setOverwrite(true);
        request.setEntries(List.of(
                entry("2026-01-04", "新日期", null),
                entry("2026-01-05", "覆盖旧日期", List.of("e"))));
        when(workLogMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(existing));

        try (MockedStatic<Db> db = mockStatic(Db.class)) {
            db.when(() -> Db.executeBatch(anyCollection(), anyInt(), eq(WorkLogMapper.class),
                            ArgumentMatchers.<BiConsumer<WorkLogMapper, WorkLog>>any()))
                    .thenAnswer(invocation -> {
                        @SuppressWarnings("unchecked")
                        Collection<WorkLog> entities = (Collection<WorkLog>) invocation.getArgument(0);
                        @SuppressWarnings("unchecked")
                        BiConsumer<WorkLogMapper, WorkLog> consumer =
                                (BiConsumer<WorkLogMapper, WorkLog>) invocation.getArgument(3);
                        entities.forEach(entity -> consumer.accept(workLogMapper, entity));
                        return new int[entities.size()];
                    });

            WorkLogImportResultVO vo = service.importEntries(request, USER_ID);

            assertEquals(2, vo.getImported());
        }

        // 插入与更新两阶段包进同一个事务回调：中途失败由事务整体回滚，而非留下部分持久化
        verify(transactionTemplate, times(1)).executeWithoutResult(any());
        verify(workLogMapper).insertBatch(anyList(), eq(1000));
        verify(workLogMapper).update(any(WorkLog.class));
    }

    @Test
    void import_writeFailsMidway_exceptionPropagatesForFullRollback() {
        WorkLog existing = existingRow(LocalDate.of(2026, 1, 5), 9L);
        WorkLogImportRequest request = new WorkLogImportRequest();
        request.setOverwrite(true);
        request.setEntries(List.of(
                entry("2026-01-04", "新日期", null),
                entry("2026-01-05", "覆盖旧日期", null)));
        when(workLogMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(existing));
        when(workLogMapper.insertBatch(anyList(), anyInt())).thenReturn(1);

        try (MockedStatic<Db> db = mockStatic(Db.class)) {
            db.when(() -> Db.executeBatch(anyCollection(), anyInt(), eq(WorkLogMapper.class),
                            ArgumentMatchers.<BiConsumer<WorkLogMapper, WorkLog>>any()))
                    .thenThrow(new RuntimeException("Data too long for column"));

            // 更新阶段失败：异常向上抛（由全局异常处理器返回错误响应），事务整体回滚插入+更新
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.importEntries(request, USER_ID));
            assertEquals("Data too long for column", ex.getMessage());
        }

        // 失败发生在同一事务回调内：插入阶段已发起，两阶段一起等待事务提交或回滚
        verify(transactionTemplate, times(1)).executeWithoutResult(any());
        verify(workLogMapper).insertBatch(anyList(), eq(1000));
        verify(workLogMapper, never()).update(any(WorkLog.class));
    }

    private WorkLogSaveRequest entry(String workDate, String title, List<String> tags) {
        WorkLogSaveRequest entry = new WorkLogSaveRequest();
        entry.setWorkDate(workDate);
        entry.setTitle(title);
        entry.setTags(tags);
        return entry;
    }

    private WorkLog existingRow(LocalDate workDate, long id) {
        WorkLog row = new WorkLog();
        row.setId(id);
        row.setUserId(USER_ID);
        row.setWorkDate(workDate);
        row.setTitle("旧标题");
        row.setTags(JSONUtil.toJsonStr(List.of("旧")));
        row.setCreateTime(LocalDateTime.of(2026, 1, 1, 8, 0));
        row.setUpdateTime(LocalDateTime.of(2026, 1, 1, 8, 0));
        return row;
    }
}
