package com.ai.service.library.impl;

import com.ai.config.LibraryProperties;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.mapper.library.LibraryCollectionMapper;
import com.ai.mapper.library.LibraryFileMapper;
import com.ai.mapper.library.LibraryTagMapper;
import com.mybatisflex.core.query.QueryWrapper;
import com.ai.model.dto.library.LibraryCollectionCreateRequest;
import com.ai.model.dto.library.LibraryFileCollectionRemoveRequest;
import com.ai.model.dto.library.LibraryFileRenameRequest;
import com.ai.model.dto.library.LibraryTagCreateRequest;
import com.ai.model.dto.library.LibraryUploadCompleteRequest;
import com.ai.model.dto.library.LibraryUploadInitRequest;
import com.ai.model.dto.library.LibraryUsageAggRow;
import com.ai.model.entity.library.LibraryCollection;
import com.ai.model.entity.library.LibraryFile;
import com.ai.model.entity.library.LibraryTag;
import com.ai.model.vo.library.LibraryCollectionVO;
import com.ai.model.vo.library.LibraryFileVO;
import com.ai.model.vo.library.LibrarySummaryVO;
import com.ai.model.vo.library.LibraryTagVO;
import com.ai.model.vo.library.LibraryUploadInitVO;
import com.ai.service.library.LibraryStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 资料库编辑类端点（§4.5）的 service 层单测：mock 三个 mapper，core 用真实实例。
 */
class LibraryFileOpsServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long TAG_ID = 2L;
    private static final Long COLLECTION_ID = 10L;

    private LibraryFileOpsService fileOps;
    private LibraryFileCoreService core;
    private LibraryFileMapper fileMapper;
    private LibraryTagMapper tagMapper;
    private LibraryCollectionMapper collectionMapper;
    private LibraryStorageService storage;
    private LibraryProperties properties;
    private LibrarySummaryCache summaryCache;

    @BeforeEach
    void setUp() {
        fileMapper = mock(LibraryFileMapper.class);
        tagMapper = mock(LibraryTagMapper.class);
        collectionMapper = mock(LibraryCollectionMapper.class);
        storage = mock(LibraryStorageService.class);
        properties = new LibraryProperties();
        summaryCache = new LibrarySummaryCache();
        core = new LibraryFileCoreService();
        ReflectionTestUtils.setField(core, "libraryFileMapper", fileMapper);
        ReflectionTestUtils.setField(core, "summaryCache", summaryCache);
        fileOps = new LibraryFileOpsService();
        ReflectionTestUtils.setField(fileOps, "core", core);
        ReflectionTestUtils.setField(fileOps, "libraryFileMapper", fileMapper);
        ReflectionTestUtils.setField(fileOps, "libraryTagMapper", tagMapper);
        ReflectionTestUtils.setField(fileOps, "libraryCollectionMapper", collectionMapper);
        ReflectionTestUtils.setField(fileOps, "storage", storage);
        ReflectionTestUtils.setField(fileOps, "properties", properties);
        ReflectionTestUtils.setField(fileOps, "summaryCache", summaryCache);
    }

    // ---------- 新建合集 ----------

    @Test
    void createCollection_defaultsBlueAndReturnsVo() {
        when(collectionMapper.selectCountByQuery(any())).thenReturn(0L);

        LibraryCollectionCreateRequest request = new LibraryCollectionCreateRequest();
        request.setName("论文书脊");
        LibraryCollectionVO vo = fileOps.createCollection(request, USER_ID);

        assertEquals("论文书脊", vo.getName());
        assertEquals("blue", vo.getColor());
        ArgumentCaptor<LibraryCollection> captor = ArgumentCaptor.forClass(LibraryCollection.class);
        verify(collectionMapper).insert(captor.capture());
        assertEquals(USER_ID, captor.getValue().getUserId());
    }

    @Test
    void createCollection_rejectsDuplicateName() {
        when(collectionMapper.selectCountByQuery(any())).thenReturn(1L);
        LibraryCollectionCreateRequest request = collectionRequest("论文书脊", null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileOps.createCollection(request, USER_ID));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("合集已存在"));
    }

    @Test
    void createCollection_rejectsBlankName() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileOps.createCollection(collectionRequest("  ", null), USER_ID));
        assertTrue(ex.getMessage().contains("合集名不能为空"));
    }

    @Test
    void createCollection_rejectsUnknownColor() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileOps.createCollection(collectionRequest("论文书脊", "pink"), USER_ID));
        assertTrue(ex.getMessage().contains("不支持的合集颜色"));
    }

    // ---------- 更新合集 ----------

    @Test
    void updateCollection_renamesCollection() {
        when(collectionMapper.selectOneByQuery(any())).thenReturn(collection(COLLECTION_ID, "旧书脊", "blue"));
        when(collectionMapper.selectCountByQuery(any())).thenReturn(0L);

        assertTrue(fileOps.updateCollection(COLLECTION_ID, collectionRequest("新书脊", null), USER_ID));

        ArgumentCaptor<LibraryCollection> captor = ArgumentCaptor.forClass(LibraryCollection.class);
        verify(collectionMapper).updateByQuery(captor.capture(), any());
        assertEquals("新书脊", captor.getValue().getName());
    }

    @Test
    void updateCollection_rejectsForeignId() {
        when(collectionMapper.selectOneByQuery(any())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileOps.updateCollection(COLLECTION_ID, collectionRequest("新书脊", null), USER_ID));
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), ex.getCode());
    }

    @Test
    void updateCollection_rejectsDuplicateName() {
        when(collectionMapper.selectOneByQuery(any())).thenReturn(collection(COLLECTION_ID, "旧书脊", "blue"));
        when(collectionMapper.selectCountByQuery(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileOps.updateCollection(COLLECTION_ID, collectionRequest("别人的书脊", null), USER_ID));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("合集已存在"));
    }

    // ---------- 删除合集：只解除关联、不删文件 ----------

    @Test
    void deleteCollection_stripsOnlyOwningFiles() {
        when(collectionMapper.selectOneByQuery(any())).thenReturn(collection(COLLECTION_ID, "书脊", "blue"));
        LibraryFile inOther = file(101L, "a.txt", null, "[10,20]", null);
        LibraryFile inRecycle = file(102L, "b.txt", null, "[10]", LocalDate.now());
        LibraryFile unrelated = file(103L, "c.txt", null, "[30]", null);
        when(fileMapper.selectListByQuery(any())).thenReturn(List.of(inOther, inRecycle, unrelated));

        assertTrue(fileOps.deleteCollection(COLLECTION_ID, USER_ID));

        // 剥离查询只投影 id 与 collection_ids，不再全列拉取 ai_summary 等大字段
        ArgumentCaptor<QueryWrapper> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(fileMapper).selectListByQuery(queryCaptor.capture());
        String sql = queryCaptor.getValue().toSQL();
        assertTrue(sql.contains("collection_ids"));
        assertFalse(sql.contains("ai_summary"));

        // f1 与回收站内的 f2 各更新一次，f3 不动
        verifyFileCollectionPatches("[20]", "[]");
        verify(collectionMapper).deleteByQuery(any());
    }

    @Test
    void deleteCollection_rejectsForeignId() {
        when(collectionMapper.selectOneByQuery(any())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileOps.deleteCollection(COLLECTION_ID, USER_ID));
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), ex.getCode());
        verify(fileMapper, never()).updateByQuery(any(LibraryFile.class), any());
        verify(collectionMapper, never()).deleteByQuery(any());
    }

    // ---------- 更新标签 ----------

    @Test
    void updateTag_renamesAndKeepsColorWhenAbsent() {
        when(tagMapper.selectOneByQuery(any())).thenReturn(tag(TAG_ID, "旧名", "sakura"));
        when(tagMapper.selectCountByQuery(any())).thenReturn(0L);

        LibraryTagVO vo = fileOps.updateTag(TAG_ID, tagRequest("新名", null), USER_ID);

        assertEquals("新名", vo.getName());
        assertEquals("sakura", vo.getColor());
        ArgumentCaptor<LibraryTag> captor = ArgumentCaptor.forClass(LibraryTag.class);
        verify(tagMapper).updateByQuery(captor.capture(), any());
        assertEquals("新名", captor.getValue().getName());
    }

    @Test
    void updateTag_rejectsDuplicateName() {
        when(tagMapper.selectOneByQuery(any())).thenReturn(tag(TAG_ID, "旧名", "sakura"));
        when(tagMapper.selectCountByQuery(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileOps.updateTag(TAG_ID, tagRequest("别人的标签", null), USER_ID));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("标签已存在"));
    }

    @Test
    void updateTag_rejectsUnknownColor() {
        when(tagMapper.selectOneByQuery(any())).thenReturn(tag(TAG_ID, "旧名", "sakura"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileOps.updateTag(TAG_ID, tagRequest("新名", "pink"), USER_ID));
        assertTrue(ex.getMessage().contains("不支持的标签颜色"));
    }

    // ---------- 删除标签：剥离所有文件上的该标签 ----------

    @Test
    void deleteTag_stripsTagFromFilesAndDeletesRow() {
        when(tagMapper.selectOneByQuery(any())).thenReturn(tag(TAG_ID, "标签", "sakura"));
        LibraryFile holder = file(201L, "a.txt", "[1,2]", null, null);
        LibraryFile onlyTag = file(202L, "b.txt", "[2]", null, null);
        LibraryFile unrelated = file(203L, "c.txt", "[3]", null, null);
        when(fileMapper.selectListByQuery(any())).thenReturn(List.of(holder, onlyTag, unrelated));

        assertTrue(fileOps.deleteTag(TAG_ID, USER_ID));

        // 剥离查询只投影 id 与 tag_ids，不再全列拉取 ai_summary 等大字段
        ArgumentCaptor<QueryWrapper> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(fileMapper).selectListByQuery(queryCaptor.capture());
        String sql = queryCaptor.getValue().toSQL();
        assertTrue(sql.contains("tag_ids"));
        assertFalse(sql.contains("ai_summary"));

        verifyFileTagPatches("[1]", "[]");
        verify(tagMapper).deleteByQuery(any());
    }

    @Test
    void deleteTag_rejectsForeignId() {
        when(tagMapper.selectOneByQuery(any())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileOps.deleteTag(TAG_ID, USER_ID));
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), ex.getCode());
        verify(fileMapper, never()).updateByQuery(any(LibraryFile.class), any());
        verify(tagMapper, never()).deleteByQuery(any());
    }

    // ---------- 文件移出合集 ----------

    @Test
    void removeFileCollection_removesCollection() {
        when(fileMapper.selectOneByQuery(any())).thenReturn(file(301L, "a.txt", null, "[10,20]", null));
        when(collectionMapper.selectOneByQuery(any())).thenReturn(collection(COLLECTION_ID, "书脊", "blue"));

        assertTrue(fileOps.removeFileCollection(301L, removeRequest(COLLECTION_ID), USER_ID));

        ArgumentCaptor<LibraryFile> captor = ArgumentCaptor.forClass(LibraryFile.class);
        verify(fileMapper).updateByQuery(captor.capture(), any());
        assertEquals("[20]", captor.getValue().getCollectionIds());
    }

    @Test
    void removeFileCollection_idempotentWhenAbsent() {
        when(fileMapper.selectOneByQuery(any())).thenReturn(file(301L, "a.txt", null, "[20]", null));
        when(collectionMapper.selectOneByQuery(any())).thenReturn(collection(COLLECTION_ID, "书脊", "blue"));

        assertTrue(fileOps.removeFileCollection(301L, removeRequest(COLLECTION_ID), USER_ID));
        verify(fileMapper, never()).updateByQuery(any(LibraryFile.class), any());
    }

    @Test
    void removeFileCollection_rejectsMissingCollection() {
        when(fileMapper.selectOneByQuery(any())).thenReturn(file(301L, "a.txt", null, "[20]", null));
        when(collectionMapper.selectOneByQuery(any())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileOps.removeFileCollection(301L, removeRequest(COLLECTION_ID), USER_ID));
        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), ex.getCode());
    }

    @Test
    void removeFileCollection_rejectsRecycledFile() {
        when(fileMapper.selectOneByQuery(any()))
                .thenReturn(file(301L, "a.txt", null, "[10,20]", LocalDate.now()));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileOps.removeFileCollection(301L, removeRequest(COLLECTION_ID), USER_ID));
        assertTrue(ex.getMessage().contains("回收站"));
    }

    // ---------- 文件重命名 ----------

    @Test
    void renameFile_updatesNameOnly() {
        LibraryFile row = file(401L, "旧名.txt", null, null, null);
        when(fileMapper.selectOneByQuery(any())).thenReturn(row);
        when(fileMapper.selectCountByQuery(any())).thenReturn(0L);

        LibraryFileVO vo = fileOps.renameFile(401L, renameRequest("新名.md"), USER_ID);

        assertEquals("新名.md", vo.getName());
        // 重命名不改扩展名：对象键由 sha256+扩展名决定
        assertEquals("txt", vo.getExt());
        ArgumentCaptor<LibraryFile> captor = ArgumentCaptor.forClass(LibraryFile.class);
        verify(fileMapper).updateByQuery(captor.capture(), any());
        assertEquals("新名.md", captor.getValue().getFileName());
    }

    @Test
    void renameFile_rejectsDuplicateActiveName() {
        when(fileMapper.selectOneByQuery(any())).thenReturn(file(401L, "旧名.txt", null, null, null));
        when(fileMapper.selectCountByQuery(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileOps.renameFile(401L, renameRequest("别人的笔记.txt"), USER_ID));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("同名文件已存在"));
    }

    @Test
    void renameFile_rejectsBlankName() {
        when(fileMapper.selectOneByQuery(any())).thenReturn(file(401L, "旧名.txt", null, null, null));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileOps.renameFile(401L, renameRequest(" "), USER_ID));
        assertTrue(ex.getMessage().contains("文件名不能为空"));
    }

    // ---------- 容量统计：SQL 聚合与 summary 缓存 ----------

    @Test
    void usedBytes_sumsViaSqlAggregation() {
        when(fileMapper.selectUsageAgg(USER_ID)).thenReturn(agg(3L, 700L));

        assertEquals(700L, core.usedBytes(USER_ID));

        verify(fileMapper).selectUsageAgg(USER_ID);
        // 配额口径不拉全量行
        verify(fileMapper, never()).selectListByQuery(any());
    }

    @Test
    void usedBytes_returnsZeroWhenAggMissingOrNull() {
        when(fileMapper.selectUsageAgg(USER_ID)).thenReturn(null);
        assertEquals(0L, core.usedBytes(USER_ID));

        when(fileMapper.selectUsageAgg(USER_ID)).thenReturn(agg(0L, null));
        assertEquals(0L, core.usedBytes(USER_ID));
    }

    @Test
    void summary_buildsFromAggAndRecentRow() {
        when(fileMapper.selectUsageAgg(USER_ID)).thenReturn(agg(2L, 300L));
        LibraryFile recent = file(501L, "最近.txt", null, null, null);
        recent.setLastOpenedAt(LocalDateTime.of(2026, 9, 20, 8, 30));
        when(fileMapper.selectOneByQuery(any())).thenReturn(recent);

        LibrarySummaryVO vo = fileOps.summary(USER_ID);

        assertEquals(2L, vo.getFileCount());
        assertEquals(300L, vo.getUsedBytes());
        assertEquals(properties.getQuotaBytes(), vo.getQuotaBytes());
        assertEquals(501L, vo.getRecentFileId());
        assertEquals("2026-09-20 08:30", vo.getRecentFileAt());
        // 不再全量拉文件行进内存
        verify(fileMapper, never()).selectListByQuery(any());
    }

    @Test
    void summary_keepsRecentEmptyWhenNothingOpened() {
        when(fileMapper.selectUsageAgg(USER_ID)).thenReturn(agg(1L, 10L));
        when(fileMapper.selectOneByQuery(any())).thenReturn(null);

        LibrarySummaryVO vo = fileOps.summary(USER_ID);

        assertNull(vo.getRecentFileId());
        assertNull(vo.getRecentFileAt());
    }

    @Test
    void summary_secondReadHitsCacheAndEvictRecomputes() {
        when(fileMapper.selectUsageAgg(USER_ID)).thenReturn(agg(2L, 300L));
        when(fileMapper.selectOneByQuery(any())).thenReturn(null);

        fileOps.summary(USER_ID);
        assertEquals(300L, fileOps.summary(USER_ID).getUsedBytes());
        verify(fileMapper, times(1)).selectUsageAgg(USER_ID);

        summaryCache.evict(USER_ID);
        fileOps.summary(USER_ID);
        verify(fileMapper, times(2)).selectUsageAgg(USER_ID);
    }

    @Test
    void uploadComplete_marksDoneAndEvictsSummaryCache() {
        LibraryFile recent = file(601L, "a.txt", null, null, null);
        recent.setLastOpenedAt(LocalDateTime.of(2026, 9, 20, 8, 30));
        LibraryFile uploading = file(601L, "a.txt", null, null, null);
        uploading.setUploadStatus("UPLOADING");
        when(fileMapper.selectOneByQuery(any())).thenReturn(recent, uploading, recent);
        when(storage.statSize(any(), any())).thenReturn(123L);
        when(fileMapper.selectUsageAgg(USER_ID)).thenReturn(agg(1L, 100L));
        fileOps.summary(USER_ID); // 预热缓存

        LibraryUploadCompleteRequest request = new LibraryUploadCompleteRequest();
        request.setFileId(601L);
        LibraryFileVO vo = fileOps.uploadComplete(request, USER_ID);

        assertEquals(123L, vo.getSize());
        ArgumentCaptor<LibraryFile> captor = ArgumentCaptor.forClass(LibraryFile.class);
        verify(fileMapper).update(captor.capture());
        assertEquals(123L, captor.getValue().getSize());
        // 失效后 summary 重新聚合
        fileOps.summary(USER_ID);
        verify(fileMapper, times(2)).selectUsageAgg(USER_ID);
    }

    @Test
    void uploadInit_instantUploadRegistersDoneAndEvictsSummaryCache() {
        LibraryFile dedup = file(701L, "旧副本.txt", null, null, null);
        dedup.setLastOpenedAt(LocalDateTime.of(2026, 9, 1, 10, 0));
        when(fileMapper.selectOneByQuery(any())).thenReturn(dedup);
        when(storage.statSize(any(), any())).thenReturn(5L);
        when(fileMapper.selectUsageAgg(USER_ID)).thenReturn(agg(1L, 100L));
        fileOps.summary(USER_ID); // 预热缓存

        LibraryUploadInitRequest request = new LibraryUploadInitRequest();
        request.setFileName("新副本.txt");
        request.setSize(10L);
        request.setSha256("a".repeat(64));
        LibraryUploadInitVO vo = fileOps.uploadInit(request, USER_ID);

        assertEquals("instant", vo.getMode());
        ArgumentCaptor<LibraryFile> captor = ArgumentCaptor.forClass(LibraryFile.class);
        verify(fileMapper).insert(captor.capture());
        assertEquals("DONE", captor.getValue().getUploadStatus());
        assertEquals(10L, captor.getValue().getSize());
        // 失效后 summary 重新聚合
        fileOps.summary(USER_ID);
        // 预热 1 次 + 配额即时校验 1 次 + 失效后重算 1 次
        verify(fileMapper, times(3)).selectUsageAgg(USER_ID);
    }

    @Test
    void open_marksOpenedAndEvictsSummaryCache() {
        LibraryFile opened = file(801L, "a.txt", null, null, null);
        opened.setLastOpenedAt(LocalDateTime.of(2026, 9, 20, 8, 30));
        when(fileMapper.selectOneByQuery(any())).thenReturn(opened);
        when(fileMapper.selectUsageAgg(USER_ID)).thenReturn(agg(1L, 100L));
        fileOps.summary(USER_ID); // 预热缓存

        assertTrue(fileOps.open(801L, USER_ID));

        ArgumentCaptor<LibraryFile> captor = ArgumentCaptor.forClass(LibraryFile.class);
        verify(fileMapper).updateByQuery(captor.capture(), any());
        assertEquals(1, captor.getValue().getOpenCount());
        // 打开也影响 summary 的最近文件，需失效缓存
        fileOps.summary(USER_ID);
        verify(fileMapper, times(2)).selectUsageAgg(USER_ID);
    }

    // ---------- 造数与断言工具 ----------

    private static LibraryCollectionCreateRequest collectionRequest(String name, String color) {
        LibraryCollectionCreateRequest request = new LibraryCollectionCreateRequest();
        request.setName(name);
        request.setColor(color);
        return request;
    }

    private static LibraryTagCreateRequest tagRequest(String name, String color) {
        LibraryTagCreateRequest request = new LibraryTagCreateRequest();
        request.setName(name);
        request.setColor(color);
        return request;
    }

    private static LibraryFileCollectionRemoveRequest removeRequest(Long collectionId) {
        LibraryFileCollectionRemoveRequest request = new LibraryFileCollectionRemoveRequest();
        request.setCollectionId(collectionId);
        return request;
    }

    private static LibraryFileRenameRequest renameRequest(String name) {
        LibraryFileRenameRequest request = new LibraryFileRenameRequest();
        request.setName(name);
        return request;
    }

    private static LibraryUsageAggRow agg(Long fileCount, Long usedBytes) {
        LibraryUsageAggRow row = new LibraryUsageAggRow();
        row.setFileCount(fileCount);
        row.setUsedBytes(usedBytes);
        return row;
    }

    private static LibraryCollection collection(Long id, String name, String color) {
        LibraryCollection collection = new LibraryCollection();
        collection.setId(id);
        collection.setUserId(USER_ID);
        collection.setName(name);
        collection.setColor(color);
        return collection;
    }

    private static LibraryTag tag(Long id, String name, String color) {
        LibraryTag tag = new LibraryTag();
        tag.setId(id);
        tag.setUserId(USER_ID);
        tag.setName(name);
        tag.setColor(color);
        return tag;
    }

    /** tagIds/collectionIds 传 JSON 字符串；deletedAt 非空表示在回收站 */
    private static LibraryFile file(Long id, String name, String tagIds,
                                    String collectionIds, LocalDate deletedAt) {
        LibraryFile file = new LibraryFile();
        file.setId(id);
        file.setUserId(USER_ID);
        file.setFileName(name);
        file.setExt("txt");
        file.setCategory("DOCUMENT");
        file.setSize(100L);
        file.setUploadStatus("DONE");
        file.setAiStatus("NONE");
        file.setTagIds(tagIds);
        file.setCollectionIds(collectionIds);
        file.setDeletedAt(deletedAt);
        file.setCreateTime(LocalDateTime.of(2026, 9, 1, 10, 0));
        return file;
    }

    /** 校验按顺序更新了哪些文件的 collection_ids JSON 剥离结果 */
    private void verifyFileCollectionPatches(String... expectedJsonList) {
        ArgumentCaptor<LibraryFile> captor = ArgumentCaptor.forClass(LibraryFile.class);
        verify(fileMapper, org.mockito.Mockito.times(expectedJsonList.length))
                .updateByQuery(captor.capture(), any());
        List<LibraryFile> patches = captor.getAllValues();
        for (int i = 0; i < expectedJsonList.length; i++) {
            assertEquals(expectedJsonList[i], patches.get(i).getCollectionIds());
        }
    }

    /** 校验按顺序更新了哪些文件的 tag_ids JSON 剥离结果 */
    private void verifyFileTagPatches(String... expectedJsonList) {
        ArgumentCaptor<LibraryFile> captor = ArgumentCaptor.forClass(LibraryFile.class);
        verify(fileMapper, org.mockito.Mockito.times(expectedJsonList.length))
                .updateByQuery(captor.capture(), any());
        List<LibraryFile> patches = captor.getAllValues();
        for (int i = 0; i < expectedJsonList.length; i++) {
            assertEquals(expectedJsonList[i], patches.get(i).getTagIds());
        }
    }
}
