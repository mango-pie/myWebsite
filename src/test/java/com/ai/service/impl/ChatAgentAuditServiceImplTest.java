package com.ai.service.impl;

import com.ai.mapper.chat.ChatAgentAuditMapper;
import com.ai.model.dto.chat.ChatAgentAuditRecord;
import com.ai.model.entity.ChatAgentAudit;
import com.ai.model.vo.chat.ChatAgentAuditVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatAgentAuditServiceImplTest {

    private ChatAgentAuditServiceImpl service;

    private ChatAgentAuditMapper mapper;

    @BeforeEach
    void setUp() {
        service = new ChatAgentAuditServiceImpl();
        mapper = mock(ChatAgentAuditMapper.class);
        ReflectionTestUtils.setField(service, "chatAgentAuditMapper", mapper);
    }

    @Test
    void record_insertsTruncatedRow() {
        String longArgs = "x".repeat(5000);
        String longSummary = "y".repeat(1000);

        service.record(ChatAgentAuditRecord.builder()
                .userId(1L)
                .conversationId(10L)
                .toolName("create_blog_draft")
                .toolArgs(longArgs)
                .success(true)
                .resultSummary(longSummary)
                .build());

        ArgumentCaptor<ChatAgentAudit> captor = ArgumentCaptor.forClass(ChatAgentAudit.class);
        verify(mapper).insert(captor.capture());
        ChatAgentAudit row = captor.getValue();
        assertEquals(1L, row.getUserId());
        assertEquals(10L, row.getConversationId());
        assertEquals("create_blog_draft", row.getToolName());
        assertEquals(4096, row.getToolArgs().length());
        assertEquals(1, row.getSuccess());
        assertEquals(512, row.getResultSummary().length());
        assertNull(row.getId());
    }

    @Test
    void record_mapsFailureToZero() {
        service.record(ChatAgentAuditRecord.builder()
                .userId(1L)
                .toolName("delete_study_task")
                .success(false)
                .resultSummary("工具执行失败")
                .build());

        ArgumentCaptor<ChatAgentAudit> captor = ArgumentCaptor.forClass(ChatAgentAudit.class);
        verify(mapper).insert(captor.capture());
        assertEquals(0, captor.getValue().getSuccess());
    }

    @Test
    void record_swallowsMapperError() {
        doThrow(new RuntimeException("db down")).when(mapper).insert(any(ChatAgentAudit.class));

        service.record(ChatAgentAuditRecord.builder()
                .userId(1L)
                .toolName("create_study_task")
                .success(true)
                .build());
    }

    @Test
    void record_ignoresNullRecord() {
        service.record(null);
        verify(mapper, never()).insert(any(ChatAgentAudit.class));
    }

    @Test
    void pageLogs_appliesUserAndToolFilter() {
        when(mapper.paginate(any(Page.class), any(QueryWrapper.class))).thenReturn(Page.of(1, 20));

        service.pageLogs(1L, "create_study_task", null, null, 1, 20);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<QueryWrapper> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper).paginate(any(Page.class), captor.capture());
        String sql = captor.getValue().toSQL();
        // MyBatis-Flex 渲染的列名带反引号
        assertTrue(sql.contains("`user_id`"), "应带当前用户归属过滤: " + sql);
        assertTrue(sql.contains("`tool_name`"), "应带工具名过滤: " + sql);
    }

    @Test
    void pageLogs_normalizesPagingAndMapsVo() {
        ChatAgentAudit row = ChatAgentAudit.builder()
                .id(5L)
                .userId(1L)
                .conversationId(10L)
                .toolName("create_blog_draft")
                .toolArgs("{}")
                .success(1)
                .resultSummary("创建草稿 5")
                .build();
        Page<ChatAgentAudit> entityPage = new Page<>(1, 20, 1);
        entityPage.setRecords(List.of(row));
        when(mapper.paginate(any(Page.class), any(QueryWrapper.class))).thenReturn(entityPage);

        Page<ChatAgentAuditVO> voPage = service.pageLogs(1L, null, null, null, 0, 500);

        assertEquals(1, voPage.getPageNumber());
        assertEquals(100, voPage.getPageSize());
        assertEquals(1, voPage.getTotalRow());
        assertEquals(1, voPage.getRecords().size());
        ChatAgentAuditVO vo = voPage.getRecords().get(0);
        assertEquals(5L, vo.getId());
        assertEquals("create_blog_draft", vo.getToolName());
        assertTrue(vo.getSuccess());
        assertFalse(vo.getSuccess() == null);
    }
}
