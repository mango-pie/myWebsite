package com.ai.service.knowledge.impl;

import com.ai.mapper.knowledge.KnowledgeDocumentMapper;
import com.ai.mapper.knowledge.KnowledgeNoteMapper;
import com.ai.mapper.knowledge.SourceDocumentMapper;
import com.ai.model.dto.knowledge.KnowledgeNoteQueryRequest;
import com.ai.model.dto.knowledge.KnowledgeNoteUpdateRequest;
import com.ai.model.entity.knowledge.KnowledgeNote;
import com.ai.model.entity.knowledge.SourceDocument;
import com.ai.model.vo.knowledge.KnowledgeNoteVO;
import com.ai.service.OpsAuditLogService;
import com.ai.service.knowledge.KnowledgeNoteService;
import com.ai.service.knowledge.LearningTreeService;
import com.ai.service.knowledge.spi.NoteBlogPublisher;
import com.mybatisflex.core.paginate.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验收脚本 I：同一 note 多 tab 编辑冲突 → 最后保存为准（后端天然语义，无锁、不覆盖来源与树关联）。
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeNoteServiceImplTest {

    @Mock
    private KnowledgeNoteMapper knowledgeNoteMapper;
    @Mock
    private SourceDocumentMapper sourceDocumentMapper;
    @Mock
    private KnowledgeDocumentMapper knowledgeDocumentMapper;
    @Mock
    private NoteBlogPublisher noteBlogPublisher;
    @Mock
    private OpsAuditLogService opsAuditLogService;
    @Mock
    private LearningTreeService learningTreeService;

    private KnowledgeNoteServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeNoteServiceImpl();
        ReflectionTestUtils.setField(service, "knowledgeNoteMapper", knowledgeNoteMapper);
        ReflectionTestUtils.setField(service, "sourceDocumentMapper", sourceDocumentMapper);
        ReflectionTestUtils.setField(service, "knowledgeDocumentMapper", knowledgeDocumentMapper);
        ReflectionTestUtils.setField(service, "noteBlogPublisher", noteBlogPublisher);
        ReflectionTestUtils.setField(service, "opsAuditLogService", opsAuditLogService);
        ReflectionTestUtils.setField(service, "learningTreeService", learningTreeService);
    }

    private KnowledgeNote ownedNote() {
        SourceDocument source = new SourceDocument();
        source.setId(100L);
        source.setUserId(1L);
        source.setSourceUrl("https://example.com/a");
        // requireOwned 的归属校验已改为列裁剪查询（selectOneByQuery），update 尾部仍走 selectOneById
        when(sourceDocumentMapper.selectOneById(100L)).thenReturn(source);
        when(sourceDocumentMapper.selectOneByQuery(any())).thenReturn(source);

        KnowledgeNote note = new KnowledgeNote();
        note.setId(50L);
        note.setSourceDocumentId(100L);
        note.setTitle("旧标题");
        note.setDistilledMd("旧内容");
        note.setTags("a");
        when(knowledgeNoteMapper.selectOneById(50L)).thenReturn(note);
        return note;
    }

    @Test
    void update_lastSaveWins() {
        ownedNote();
        KnowledgeNoteUpdateRequest request = new KnowledgeNoteUpdateRequest();
        request.setTitle("新标题");
        request.setDistilledMd("新内容");
        request.setTags("a,b");

        KnowledgeNoteVO vo = service.update(50L, request, 1L);

        assertEquals("新标题", vo.getTitle());
        ArgumentCaptor<KnowledgeNote> captor = ArgumentCaptor.forClass(KnowledgeNote.class);
        verify(knowledgeNoteMapper).update(captor.capture());
        KnowledgeNote saved = captor.getValue();
        assertEquals("新标题", saved.getTitle());
        assertEquals("新内容", saved.getDistilledMd());
        assertEquals("a,b", saved.getTags());
        assertNotNull(saved.getLastEditedAt());
    }

    @Test
    void update_marksSyncAndReindexRequiredWhenLinked() {
        KnowledgeNote note = ownedNote();
        note.setBlogPostId(9L);
        note.setKnowledgeDocumentId(8L);
        KnowledgeNoteUpdateRequest request = new KnowledgeNoteUpdateRequest();
        request.setDistilledMd("改过了");

        service.update(50L, request, 1L);

        ArgumentCaptor<KnowledgeNote> captor = ArgumentCaptor.forClass(KnowledgeNote.class);
        verify(knowledgeNoteMapper).update(captor.capture());
        assertEquals("SYNC_REQUIRED", captor.getValue().getPublishStatus());
        assertEquals("REINDEX_REQUIRED", captor.getValue().getIndexStatus());
        assertEquals(LocalDateTime.now().toLocalDate(), captor.getValue().getLastEditedAt().toLocalDate());
    }

    @Test
    void page_searchPushedDownToSqlAndMapsVo() {
        when(knowledgeNoteMapper.countSearch(1L, "%Java 集合%", "url", null, null, null, 0)).thenReturn(1L);
        KnowledgeNote note = new KnowledgeNote();
        note.setId(50L);
        note.setSourceDocumentId(100L);
        note.setTitle("Java 集合精读");
        note.setTags("java");
        note.setUpdateTime(LocalDateTime.now());
        when(knowledgeNoteMapper.selectSearchPage(1L, "%Java 集合%", "url", null, null, null, 0, 0L, 10))
                .thenReturn(List.of(note));
        SourceDocument source = new SourceDocument();
        source.setId(100L);
        source.setSourceType("url");
        source.setSourceUrl("https://example.com/a");
        when(sourceDocumentMapper.selectListByQuery(any())).thenReturn(List.of(source));

        KnowledgeNoteQueryRequest request = new KnowledgeNoteQueryRequest();
        request.setKeyword("Java 集合");
        request.setSourceType("url");

        Page<KnowledgeNoteVO> page = service.page(request, 1L);

        assertEquals(1, page.getTotalRow());
        assertEquals(1, page.getRecords().size());
        KnowledgeNoteVO vo = page.getRecords().get(0);
        assertEquals("Java 集合精读", vo.getTitle());
        assertEquals("url", vo.getSourceType());
        assertEquals("https://example.com/a", vo.getSourceUrl());
    }

    @Test
    void page_escapesLikeWildcardsAndShortCircuitsOnEmptyCount() {
        when(knowledgeNoteMapper.countSearch(1L, "%50\\%\\_off\\\\%", null, null, null, null, 0)).thenReturn(0L);

        KnowledgeNoteQueryRequest request = new KnowledgeNoteQueryRequest();
        request.setKeyword("50%_off\\");

        Page<KnowledgeNoteVO> page = service.page(request, 1L);

        assertEquals(0, page.getTotalRow());
        assertTrue(page.getRecords().isEmpty());
        verify(knowledgeNoteMapper, never()).selectSearchPage(any(), any(), any(), any(), any(), any(), any(),
                anyLong(), anyInt());
    }
}
