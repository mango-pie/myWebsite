package com.ai.agent.confirm;

import com.ai.agent.config.ChatAgentProperties;
import com.ai.model.dto.chat.ChatAgentAuditRecord;
import com.ai.service.ChatAgentAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * L2 确认取消路径（设计文档 §8.3）：本人取消生效并落审计，重复/他人/空令牌取消一律拒绝。
 */
class ChatAgentConfirmServiceTest {

    private ChatAgentConfirmService service;

    private PendingConfirmStore store;

    private ChatAgentAuditService auditService;

    @BeforeEach
    void setUp() {
        store = new PendingConfirmStore(new ChatAgentProperties());
        auditService = mock(ChatAgentAuditService.class);
        service = new ChatAgentConfirmService();
        ReflectionTestUtils.setField(service, "pendingConfirmStore", store);
        ReflectionTestUtils.setField(service, "chatAgentAuditService", auditService);
    }

    @Test
    void cancel_removesPendingAndAudits() {
        PendingConfirm pending = createPending(1L);

        assertTrue(service.cancel(1L, pending.confirmToken()));

        // 取消后令牌作废，无法再确认执行
        assertNull(store.consume(pending.confirmToken(), 1L));
        ArgumentCaptor<ChatAgentAuditRecord> captor = ArgumentCaptor.forClass(ChatAgentAuditRecord.class);
        verify(auditService).record(captor.capture());
        ChatAgentAuditRecord audit = captor.getValue();
        assertEquals(1L, audit.getUserId());
        assertEquals(10L, audit.getConversationId());
        assertEquals("publish_blog_post", audit.getToolName());
        assertFalse(audit.isSuccess());
        assertEquals("用户取消，未执行", audit.getResultSummary());
    }

    @Test
    void cancel_repeatedCancelReturnsFalse() {
        PendingConfirm pending = createPending(1L);

        assertTrue(service.cancel(1L, pending.confirmToken()));
        assertFalse(service.cancel(1L, pending.confirmToken()));
        // 只有第一次取消落审计，第二次无内容可取消
        verify(auditService, times(1)).record(any(ChatAgentAuditRecord.class));
    }

    @Test
    void cancel_otherUserCannotCancelAndPendingStays() {
        PendingConfirm pending = createPending(1L);

        assertFalse(service.cancel(2L, pending.confirmToken()));
        verify(auditService, never()).record(any(ChatAgentAuditRecord.class));

        // 原用户的待确认仍然有效，可正常确认执行
        PendingConfirm consumed = store.consume(pending.confirmToken(), 1L);
        assertEquals(pending.confirmToken(), consumed.confirmToken());
    }

    @Test
    void cancel_blankTokenOrUnknownTokenReturnsFalse() {
        assertFalse(service.cancel(1L, null));
        assertFalse(service.cancel(1L, ""));
        assertFalse(service.cancel(1L, "cfm_not_exist"));
        assertFalse(service.cancel(null, "cfm_x"));
        verify(auditService, never()).record(any(ChatAgentAuditRecord.class));
    }

    private PendingConfirm createPending(Long userId) {
        com.ai.agent.model.AgentToolContext context = com.ai.agent.model.AgentToolContext.builder()
                .userId(userId)
                .conversationId(10L)
                .configId("dania")
                .build();
        return store.create("publish_blog_post", "{\"id\":42}", context);
    }
}
