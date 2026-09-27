package com.ai.service.impl;

import com.ai.mapper.chat.ChatConversationMapper;
import com.ai.model.entity.ChatConversation;
import com.ai.model.vo.chat.ChatConversationVO;
import com.ai.service.ChatMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会话列表的 service 层单测：mock 会话 mapper（注入 ServiceImpl 的 mapper 字段）与消息服务，
 * 验证 messageCount 由一次批量计数装配，不再逐会话 COUNT。
 */
class ChatConversationServiceImplTest {

    private static final Long USER_ID = 1L;

    private ChatConversationServiceImpl service;
    private ChatConversationMapper conversationMapper;
    private ChatMessageService chatMessageService;

    @BeforeEach
    void setUp() {
        service = new ChatConversationServiceImpl();
        conversationMapper = mock(ChatConversationMapper.class);
        chatMessageService = mock(ChatMessageService.class);
        ReflectionTestUtils.setField(service, "mapper", conversationMapper);
        ReflectionTestUtils.setField(service, "chatMessageService", chatMessageService);
    }

    @Test
    void listConversations_batchCountsAndAssemblesVos() {
        ChatConversation withMessages = conversation(11L, "默认会话", 1);
        ChatConversation zeroCount = conversation(12L, "清空过消息", 0);
        ChatConversation noMessages = conversation(13L, "从没发过消息", 0);
        when(conversationMapper.selectListByQuery(any()))
                .thenReturn(List.of(withMessages, zeroCount, noMessages));
        when(chatMessageService.countByConversations(any()))
                .thenReturn(Map.of(11L, 3L, 12L, 0L));

        List<ChatConversationVO> vos = service.listConversations(USER_ID, null);

        // 批量计数只取回一次，且拿到的是全部会话 id
        verify(chatMessageService).countByConversations(
                argThat(ids -> List.of(11L, 12L, 13L).equals(ids)));

        // 字段、顺序与计数装配：有消息 3、Map 中显式 0、Map 缺席按 0
        assertEquals(3, vos.size());
        assertEquals(11L, vos.get(0).getId());
        assertEquals(3L, vos.get(0).getMessageCount());
        assertTrue(vos.get(0).getIsDefault());
        assertEquals(12L, vos.get(1).getId());
        assertEquals(0L, vos.get(1).getMessageCount());
        assertFalse(vos.get(1).getIsDefault());
        assertEquals(13L, vos.get(2).getId());
        assertEquals(0L, vos.get(2).getMessageCount());

        // 不再逐会话 COUNT
        verify(chatMessageService, never()).countByConversation(any());
    }

    @Test
    void listConversations_emptyListStillBatchesOnce() {
        when(conversationMapper.selectListByQuery(any())).thenReturn(List.of());

        List<ChatConversationVO> vos = service.listConversations(USER_ID, "config-a");

        assertTrue(vos.isEmpty());
        verify(chatMessageService).countByConversations(
                argThat(ids -> ids != null && ids.isEmpty()));
        verify(chatMessageService, never()).countByConversation(any());
    }

    private static ChatConversation conversation(Long id, String title, Integer isDefault) {
        return ChatConversation.builder()
                .id(id)
                .userId(USER_ID)
                .configId("config-a")
                .title(title)
                .isDefault(isDefault)
                .lastMessageAt(LocalDateTime.of(2026, 9, 1, 10, 0))
                .createTime(LocalDateTime.of(2026, 9, 1, 9, 0))
                .build();
    }
}
