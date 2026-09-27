package com.ai.service.library.impl;

import com.ai.config.LibraryProperties;
import com.ai.mapper.library.LibraryFileMapper;
import com.ai.model.dto.library.LibraryIdsRequest;
import com.ai.model.dto.library.LibraryUsageAggRow;
import com.ai.model.entity.library.LibraryFile;
import com.ai.service.library.LibraryStorageService;
import com.mybatisflex.core.query.QueryConditionBuilder;
import com.mybatisflex.core.update.UpdateChain;
import com.mybatisflex.core.util.LambdaGetter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 资料库回收站的 service 层单测：mock mapper / 事务模板 / 存储与向量，
 * core 与 summary 缓存用真实实例，以 summary 的重复聚合次数观察缓存是否被失效。
 */
class LibraryRecycleServiceTest {

    private static final Long USER_ID = 1L;

    private LibraryRecycleService recycleService;
    private LibraryFileOpsService fileOps;
    private LibraryFileMapper fileMapper;
    private LibraryStorageService storage;
    private LibraryVectorService vectorService;
    private TransactionTemplate transactionTemplate;
    private LibrarySummaryCache summaryCache;

    @BeforeEach
    void setUp() {
        fileMapper = mock(LibraryFileMapper.class);
        storage = mock(LibraryStorageService.class);
        vectorService = mock(LibraryVectorService.class);
        transactionTemplate = mock(TransactionTemplate.class);
        summaryCache = new LibrarySummaryCache();
        LibraryFileCoreService core = new LibraryFileCoreService();
        ReflectionTestUtils.setField(core, "libraryFileMapper", fileMapper);
        ReflectionTestUtils.setField(core, "summaryCache", summaryCache);
        recycleService = new LibraryRecycleService();
        ReflectionTestUtils.setField(recycleService, "core", core);
        ReflectionTestUtils.setField(recycleService, "libraryFileMapper", fileMapper);
        ReflectionTestUtils.setField(recycleService, "storage", storage);
        ReflectionTestUtils.setField(recycleService, "properties", new LibraryProperties());
        ReflectionTestUtils.setField(recycleService, "summaryCache", summaryCache);
        ReflectionTestUtils.setField(recycleService, "vectorService", vectorService);
        ReflectionTestUtils.setField(recycleService, "transactionTemplate", transactionTemplate);
        fileOps = new LibraryFileOpsService();
        ReflectionTestUtils.setField(fileOps, "core", core);
        ReflectionTestUtils.setField(fileOps, "libraryFileMapper", fileMapper);
        ReflectionTestUtils.setField(fileOps, "properties", new LibraryProperties());
        ReflectionTestUtils.setField(fileOps, "summaryCache", summaryCache);
    }

    // ---------- 移入回收站 ----------

    @Test
    void deleteFiles_softDeletesAndEvictsSummaryCache() {
        when(fileMapper.selectUsageAgg(USER_ID)).thenReturn(agg(2L, 200L));
        fileOps.summary(USER_ID); // 预热缓存
        when(fileMapper.updateByQuery(any(LibraryFile.class), any())).thenReturn(2);

        assertTrue(recycleService.deleteFiles(ids(11L, 12L), USER_ID));

        ArgumentCaptor<LibraryFile> captor = ArgumentCaptor.forClass(LibraryFile.class);
        verify(fileMapper).updateByQuery(captor.capture(), any());
        assertNotNull(captor.getValue().getDeletedAt());
        // 移入回收站改变 active 容量，缓存失效后重新聚合
        fileOps.summary(USER_ID);
        verify(fileMapper, times(2)).selectUsageAgg(USER_ID);
    }

    // ---------- 还原 ----------

    @Test
    void restore_restoresAndEvictsSummaryCache() {
        when(fileMapper.selectUsageAgg(USER_ID)).thenReturn(agg(1L, 100L));
        fileOps.summary(USER_ID); // 预热缓存

        try (MockedStatic<UpdateChain> updateChain = mockStatic(UpdateChain.class)) {
            @SuppressWarnings("unchecked")
            UpdateChain<LibraryFile> chain = mock(UpdateChain.class);
            @SuppressWarnings("unchecked")
            QueryConditionBuilder<UpdateChain<LibraryFile>> builder = mock(QueryConditionBuilder.class);
            updateChain.when(() -> UpdateChain.of(LibraryFile.class)).thenReturn(chain);
            when(chain.set(any(LambdaGetter.class), any())).thenReturn(chain);
            when(chain.where(any(LambdaGetter.class))).thenReturn(builder);
            when(chain.and(any(LambdaGetter.class))).thenReturn(builder);
            when(builder.eq(any(Long.class))).thenReturn(chain);
            when(builder.isNotNull()).thenReturn(chain);
            when(builder.in(anyList())).thenReturn(chain);
            when(chain.update()).thenReturn(true);

            assertTrue(recycleService.restore(ids(21L), USER_ID));
            verify(chain).update();
        }

        // 还原改变 active 容量，缓存失效后重新聚合
        fileOps.summary(USER_ID);
        verify(fileMapper, times(2)).selectUsageAgg(USER_ID);
    }

    // ---------- 彻底删除 ----------

    @Test
    void purge_deletesRowsAndEvictsSummaryCache() {
        when(fileMapper.selectUsageAgg(USER_ID)).thenReturn(agg(1L, 100L));
        fileOps.summary(USER_ID); // 预热缓存
        when(fileMapper.selectListByQuery(any())).thenReturn(List.of(file(31L)));
        when(fileMapper.selectCountByQuery(any())).thenReturn(0L);
        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        assertTrue(recycleService.purge(ids(31L), USER_ID));

        verify(fileMapper).deleteByQuery(any());
        // 无其余引用 → 清理对象与向量
        verify(storage).remove(any(), any());
        verify(vectorService).deleteByFileId(31L);
        // 宁多勿漏：彻底删除虽只作用于回收站行，也失效缓存
        fileOps.summary(USER_ID);
        verify(fileMapper, times(2)).selectUsageAgg(USER_ID);
    }

    // ---------- 造数与断言工具 ----------

    private static LibraryIdsRequest ids(Long... ids) {
        LibraryIdsRequest request = new LibraryIdsRequest();
        request.setIds(List.of(ids));
        return request;
    }

    private static LibraryUsageAggRow agg(Long fileCount, Long usedBytes) {
        LibraryUsageAggRow row = new LibraryUsageAggRow();
        row.setFileCount(fileCount);
        row.setUsedBytes(usedBytes);
        return row;
    }

    /** 回收站内文件行（deletedAt 非空），供 purge 选取 */
    private static LibraryFile file(Long id) {
        LibraryFile file = new LibraryFile();
        file.setId(id);
        file.setUserId(USER_ID);
        file.setFileName("a.txt");
        file.setExt("txt");
        file.setCategory("DOCUMENT");
        file.setSize(100L);
        file.setUploadStatus("DONE");
        file.setAiStatus("NONE");
        file.setObjectKey("library/1/202609/key.txt");
        file.setDeletedAt(LocalDate.now().minusDays(1));
        file.setDeleteTime(LocalDateTime.now().minusDays(1));
        return file;
    }
}
