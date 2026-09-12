package com.ai.service.knowledge.impl;

import com.ai.mapper.knowledge.KnowledgeNoteMapper;
import com.ai.mapper.knowledge.SourceDocumentMapper;
import com.ai.model.dto.knowledge.KnowledgeIngestBatchUrlRequest;
import com.ai.model.entity.knowledge.KnowledgeNote;
import com.ai.model.entity.knowledge.SourceDocument;
import com.ai.model.vo.knowledge.KnowledgeIngestBatchUrlVO;
import com.ai.service.BizStatDailyService;
import com.ai.service.knowledge.KnowledgeIngestionService;
import com.ai.setting.runtime.ReadingRuntimeSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 合蒸蒸馏事务边界重构（docs/p0-tx-and-scheduler-refactor.md 方案 B）的补偿语义：
 * ingest 落库后蒸馏失败 → 补偿软删孤儿 source_document，等价恢复原大事务回滚；
 * 蒸馏成功 → 正常建笔记，不做任何补偿。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KnowledgeMergeDistillServiceImplTest {

    @Mock
    private KnowledgeDeepSeekReadingService deepSeekReadingService;

    @Mock
    private KnowledgeIngestionService ingestionService;

    @Mock
    private KnowledgeNoteMapper knowledgeNoteMapper;

    @Mock
    private SourceDocumentMapper sourceDocumentMapper;

    @Mock
    private ReadingRuntimeSettings readingRuntimeSettings;

    @Mock
    private BizStatDailyService bizStatDailyService;

    @InjectMocks
    private KnowledgeMergeDistillServiceImpl service;

    private KnowledgeIngestBatchUrlRequest request;

    @BeforeEach
    void setUp() {
        request = new KnowledgeIngestBatchUrlRequest();
        request.setUrls(List.of("https://example.com/a"));
        request.setSourceType("AGENT");

        when(readingRuntimeSettings.distillTemperature()).thenReturn(0.2);
        when(readingRuntimeSettings.distillMaxTokens()).thenReturn(2048);
    }

    private void stubMaterializeAndIngest() {
        KnowledgeDeepSeekReadingService.MaterialSource source =
                new KnowledgeDeepSeekReadingService.MaterialSource(
                        "https://example.com/a", "示例标题", "SUCCESS", "# 正文材料", null);
        when(deepSeekReadingService.materialize(any(), any()))
                .thenReturn(new KnowledgeDeepSeekReadingService.MaterialBundle(List.of(source), List.of()));

        SourceDocument persisted = new SourceDocument();
        persisted.setId(100L);
        when(ingestionService.ingestAgentResult(anyString(), anyString(), anyString(), isNull(), eq(9L)))
                .thenReturn(persisted);
    }

    @Test
    void distillFailure_compensatesOrphanSourceDocument() {
        stubMaterializeAndIngest();
        when(deepSeekReadingService.distill(anyString(), anyString(), any(), anyInt()))
                .thenThrow(new RuntimeException("DeepSeek 不可达"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.mergeDistill(request, 9L));

        assertEquals("DeepSeek 不可达", ex.getMessage());
        // 补偿：孤儿 source_document 被逻辑删除
        verify(sourceDocumentMapper).deleteById(100L);
        // 笔记从未创建
        verify(knowledgeNoteMapper, never()).insert(any(KnowledgeNote.class));
    }

    @Test
    void distillSuccess_createsNoteWithoutCompensation() {
        stubMaterializeAndIngest();
        when(deepSeekReadingService.distill(anyString(), anyString(), any(), anyInt()))
                .thenReturn("# 精读文章\n\n内容");

        KnowledgeIngestBatchUrlVO vo = service.mergeDistill(request, 9L);

        assertTrue(vo.isSuccess());
        assertNotNull(vo.getTitle());
        verify(knowledgeNoteMapper).insert(any(KnowledgeNote.class));
        verify(sourceDocumentMapper, never()).deleteById(any());
    }
}
