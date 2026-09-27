package com.ai.service.impl;

import com.ai.mapper.chat.ChatMessageMapper;
import com.ai.model.dto.chat.ChatMessageCountRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * countByConversations 的单测：mock 消息 mapper，验证 GROUP BY 行装配成
 * conversationId -> count 的 Map；空集合直接短路不落库。
 */
class ChatMessageServiceImplTest {

    private ChatMessageServiceImpl service;
    private ChatMessageMapper messageMapper;

    @BeforeEach
    void setUp() {
        service = new ChatMessageServiceImpl();
        messageMapper = mock(ChatMessageMapper.class);
        ReflectionTestUtils.setField(service, "mapper", messageMapper);
    }

    @Test
    void countByConversations_mapsGroupedRowsByKey() {
        ChatMessageCountRow first = new ChatMessageCountRow();
        first.setConversationId(21L);
        first.setMessageCount(5L);
        ChatMessageCountRow second = new ChatMessageCountRow();
        second.setConversationId(22L);
        second.setMessageCount(1L);
        when(messageMapper.selectMessageCountGrouped(anyList()))
                .thenReturn(List.of(first, second));

        Map<Long, Long> counts = service.countByConversations(List.of(21L, 22L, 23L));

        // 23 号会话无消息，GROUP BY 不产生行，Map 中缺席由调用方按 0 处理
        assertEquals(Map.of(21L, 5L, 22L, 1L), counts);
    }

    @Test
    void countByConversations_emptyOrNullOrInputSkipsQuery() {
        assertTrue(service.countByConversations(List.of()).isEmpty());
        assertTrue(service.countByConversations(null).isEmpty());
        verifyNoInteractions(messageMapper);
    }
}
