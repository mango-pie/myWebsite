package com.ai.agent.registry;

import com.ai.agent.config.ChatAgentProperties;
import com.ai.agent.confirm.ConfirmRequiredException;
import com.ai.agent.confirm.PendingConfirmStore;
import com.ai.agent.model.AgentToolContext;
import com.ai.agent.model.AgentToolLevel;
import com.ai.agent.model.AgentToolResult;
import com.ai.exception.BusinessException;
import com.ai.model.dto.chat.ChatAgentAuditRecord;
import com.ai.service.ChatAgentAuditService;
import com.ai.setting.runtime.ChatRuntimeSettings;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L2 确认流网关层端到端（设计文档 §8.3）：
 * 发起确认（挂起不执行）→ 确认执行（原参数重放 + 审计）→ 重复确认幂等 → 取消后确认被拒。
 */
class AgentToolGatewayConfirmTest {

    /** 工具执行计数：静态供嵌套模块的 lambda 访问，每个用例前置归零。 */
    private static final AtomicInteger EXECUTOR_INVOCATIONS = new AtomicInteger();

    private AgentToolGateway gateway;

    private PendingConfirmStore store;

    private ChatAgentAuditService auditService;

    @BeforeEach
    void setUp() {
        EXECUTOR_INVOCATIONS.set(0);
        store = new PendingConfirmStore(new ChatAgentProperties());
        auditService = mock(ChatAgentAuditService.class);
        gateway = new AgentToolGateway();
        AgentToolRegistry registry = new AgentToolRegistry(List.of(new TestToolModule()));
        registry.init();

        ChatRuntimeSettings chatRuntimeSettings = mock(ChatRuntimeSettings.class);
        when(chatRuntimeSettings.agentL2Enabled()).thenReturn(true);

        ReflectionTestUtils.setField(gateway, "agentToolRegistry", registry);
        ReflectionTestUtils.setField(gateway, "chatRuntimeSettings", chatRuntimeSettings);
        ReflectionTestUtils.setField(gateway, "pendingConfirmStore", store);
        ReflectionTestUtils.setField(gateway, "chatAgentAuditService", auditService);
    }

    @Test
    void l2ExecuteSuspendsInsteadOfExecuting() {
        ConfirmRequiredException e = assertThrows(ConfirmRequiredException.class,
                () -> gateway.execute("danger_tool", "{\"id\":42}", context(1L, 10L)));

        // 期望：不执行工具，只挂起并要求确认
        assertEquals(0, EXECUTOR_INVOCATIONS.get());
        assertTrue(e.getConfirmToken().startsWith("cfm_"));
        assertEquals("danger_tool", e.getToolName());
        assertEquals("删除危险数据（id=42）", e.getSummary());
        verify(auditService, never()).record(any(ChatAgentAuditRecord.class));
    }

    @Test
    void confirmFlow_endToEnd_suspendThenConfirmExecutesAndAudits() {
        ConfirmRequiredException confirmEx = requireConfirm(() -> gateway.execute("danger_tool", "{\"id\":42}", context(1L, 10L)));

        ConfirmedExecution execution = gateway.executeConfirmed(confirmEx.getConfirmToken(), context(1L, 10L));

        // 确认后原参数重放执行，且落审计表
        assertEquals(1, EXECUTOR_INVOCATIONS.get());
        assertEquals("danger_tool", execution.toolName());
        assertEquals("{\"id\":42}", execution.toolArgs());
        assertTrue(execution.result().isSuccess());
        assertEquals(42L, execution.result().getData().get("taskId"));
        assertNotNull(execution.summary());

        ArgumentCaptor<ChatAgentAuditRecord> captor = ArgumentCaptor.forClass(ChatAgentAuditRecord.class);
        verify(auditService, times(1)).record(captor.capture());
        ChatAgentAuditRecord audit = captor.getValue();
        assertEquals(1L, audit.getUserId());
        assertEquals(10L, audit.getConversationId());
        assertEquals("danger_tool", audit.getToolName());
        assertEquals("{\"id\":42}", audit.getToolArgs());
        assertTrue(audit.isSuccess());
    }

    @Test
    void confirmFlow_repeatedConfirmIsIdempotent() {
        ConfirmRequiredException confirmEx = requireConfirm(() -> gateway.execute("danger_tool", "{\"id\":42}", context(1L, 10L)));

        gateway.executeConfirmed(confirmEx.getConfirmToken(), context(1L, 10L));
        // 第二次确认：令牌一次性消费，工具不会被执行两次
        assertThrows(BusinessException.class,
                () -> gateway.executeConfirmed(confirmEx.getConfirmToken(), context(1L, 10L)));
        assertEquals(1, EXECUTOR_INVOCATIONS.get());
        verify(auditService, times(1)).record(any(ChatAgentAuditRecord.class));
    }

    @Test
    void confirmFlow_rejectsWithoutExecutionWhenTokenUnknown() {
        assertThrows(BusinessException.class,
                () -> gateway.executeConfirmed("cfm_not_exist", context(1L, 10L)));
        assertEquals(0, EXECUTOR_INVOCATIONS.get());
        verify(auditService, never()).record(any(ChatAgentAuditRecord.class));
    }

    @Test
    void confirmFlow_rejectsOtherUsersToken() {
        ConfirmRequiredException confirmEx = requireConfirm(() -> gateway.execute("danger_tool", "{\"id\":42}", context(1L, 10L)));

        // 他人令牌：拒绝且不消费，原用户仍可确认
        assertThrows(BusinessException.class,
                () -> gateway.executeConfirmed(confirmEx.getConfirmToken(), context(2L, 10L)));
        assertEquals(0, EXECUTOR_INVOCATIONS.get());

        gateway.executeConfirmed(confirmEx.getConfirmToken(), context(1L, 10L));
        assertEquals(1, EXECUTOR_INVOCATIONS.get());
    }

    @Test
    void confirmFlow_rejectsConversationMismatch() {
        ConfirmRequiredException confirmEx = requireConfirm(() -> gateway.execute("danger_tool", "{\"id\":42}", context(1L, 10L)));

        assertThrows(BusinessException.class,
                () -> gateway.executeConfirmed(confirmEx.getConfirmToken(), context(1L, 99L)));
        assertEquals(0, EXECUTOR_INVOCATIONS.get());
    }

    @Test
    void confirmFlow_cancelledPendingCannotBeConfirmed() {
        ConfirmRequiredException confirmEx = requireConfirm(() -> gateway.execute("danger_tool", "{\"id\":42}", context(1L, 10L)));

        assertTrue(store.revoke(confirmEx.getConfirmToken(), 1L));

        assertThrows(BusinessException.class,
                () -> gateway.executeConfirmed(confirmEx.getConfirmToken(), context(1L, 10L)));
        assertEquals(0, EXECUTOR_INVOCATIONS.get());
    }

    @Test
    void confirmFlow_auditsExecutionFailure() {
        ConfirmRequiredException confirmEx = requireConfirm(() -> gateway.execute("failing_tool", "{\"id\":42}", context(1L, 10L)));

        ConfirmedExecution execution = gateway.executeConfirmed(confirmEx.getConfirmToken(), context(1L, 10L));

        assertFalse(execution.result().isSuccess());
        ArgumentCaptor<ChatAgentAuditRecord> captor = ArgumentCaptor.forClass(ChatAgentAuditRecord.class);
        verify(auditService, times(1)).record(captor.capture());
        assertFalse(captor.getValue().isSuccess());
        assertEquals("failing_tool", captor.getValue().getToolName());
    }

    @Test
    void l2ExecuteStillBlockedWhenDisabled() {
        ChatRuntimeSettings disabled = mock(ChatRuntimeSettings.class);
        when(disabled.agentL2Enabled()).thenReturn(false);
        ReflectionTestUtils.setField(gateway, "chatRuntimeSettings", disabled);

        AgentToolResult result = gateway.execute("danger_tool", "{\"id\":42}", context(1L, 10L));

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("尚未开放"));
        assertEquals(0, EXECUTOR_INVOCATIONS.get());
        verify(auditService, never()).record(any(ChatAgentAuditRecord.class));
    }

    @Test
    void execute_l0l1StillAudited() {
        AgentToolResult result = gateway.execute("echo_tool", "{\"value\":\"hi\"}", context(1L, 10L));

        assertTrue(result.isSuccess());
        ArgumentCaptor<ChatAgentAuditRecord> captor = ArgumentCaptor.forClass(ChatAgentAuditRecord.class);
        verify(auditService, times(1)).record(captor.capture());
        assertTrue(captor.getValue().isSuccess());
    }

    private AgentToolContext context(Long userId, Long conversationId) {
        return AgentToolContext.builder()
                .userId(userId)
                .conversationId(conversationId)
                .configId("dania")
                .build();
    }

    private ConfirmRequiredException requireConfirm(Runnable action) {
        try {
            action.run();
        } catch (ConfirmRequiredException e) {
            return e;
        }
        throw new AssertionError("应抛出 ConfirmRequiredException");
    }

    /** 测试工具模块：一个 L2 工具（带确认摘要）、一个必然失败的 L2 工具、一个 L0 工具。 */
    static class TestToolModule extends com.ai.agent.registry.AgentToolModule {

        @Override
        public String moduleName() {
            return "test";
        }

        @Override
        protected void registerTools(AgentToolRegistryBuilder registry) {
            registry.l2("danger_tool", "删除危险数据",
                    JsonObjectSchema.builder().build(),
                    (ctx, args) -> "删除危险数据（id=42）",
                    (ctx, args) -> {
                        EXECUTOR_INVOCATIONS.incrementAndGet();
                        return AgentToolResult.ok(AgentToolRegistryBuilder.mapOf("taskId", 42L));
                    });
            registry.l2("failing_tool", "必然失败",
                    JsonObjectSchema.builder().build(),
                    (ctx, args) -> AgentToolResult.fail("业务校验失败"));
            registry.register(
                    "echo_tool",
                    "echo",
                    AgentToolLevel.L0,
                    JsonObjectSchema.builder().build(),
                    (ctx, args) -> AgentToolResult.ok(AgentToolRegistryBuilder.mapOf("value", "hi"))
            );
        }
    }
}
