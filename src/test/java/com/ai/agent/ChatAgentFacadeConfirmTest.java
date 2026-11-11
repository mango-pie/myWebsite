package com.ai.agent;

import com.ai.agent.config.ChatAgentProperties;
import com.ai.agent.confirm.ConfirmRequiredException;
import com.ai.agent.model.AgentToolContext;
import com.ai.agent.registry.AgentToolRegistry;
import com.ai.agent.registry.ConfirmedExecution;
import com.ai.agent.registry.AgentToolGateway;
import com.ai.agent.registry.AgentToolRegistryBuilder;
import com.ai.agent.support.ChatConversationSupport;
import com.ai.model.dto.chat.ChatMessageSegment;
import com.ai.model.entity.ChatConversation;
import com.ai.model.entity.User;
import com.ai.model.enums.MessageTypeEnum;
import com.ai.model.vo.chat.ChatStreamEvent;
import com.ai.service.AiUsageLogService;
import com.ai.service.ChatConversationService;
import com.ai.service.ChatMessageService;
import com.ai.service.UserService;
import com.ai.setting.IntegrationOpenAiModelFactory;
import com.ai.setting.runtime.ChatRuntimeSettings;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Agent 门面确认流接线（设计文档 §8.3）：
 * L2 触发 → confirm_required 事件暂停本轮；确认续跑 → tool_call/tool_result/chunk/done。
 */
class ChatAgentFacadeConfirmTest {

    private static final String SSE_TYPE = "agent";

    private ChatAgentFacade facade;

    private AgentToolGateway agentToolGateway;

    private ChatMessageService chatMessageService;

    private ChatConversationService chatConversationService;

    @BeforeEach
    void setUp() {
        facade = new ChatAgentFacade();
        agentToolGateway = mock(AgentToolGateway.class);
        chatMessageService = mock(ChatMessageService.class);
        chatConversationService = mock(ChatConversationService.class);

        UserService userService = mock(UserService.class);
        User loginUser = new User();
        loginUser.setId(1L);
        when(userService.getLoginUser(any(HttpServletRequest.class))).thenReturn(loginUser);

        ChatConversationSupport conversationSupport = mock(ChatConversationSupport.class);
        ChatConversation conversation = new ChatConversation();
        conversation.setId(10L);
        conversation.setConfigId("dania");
        when(conversationSupport.resolveConversation(1L, 10L, null)).thenReturn(conversation);

        when(chatMessageService.listLatest(anyLong(), anyLong(), anyInt())).thenReturn(List.of());

        ChatRuntimeSettings chatRuntimeSettings = mock(ChatRuntimeSettings.class);
        when(chatRuntimeSettings.agentMaxSteps()).thenReturn(8);
        when(chatRuntimeSettings.agentHistoryLimit()).thenReturn(20);
        when(chatRuntimeSettings.agentL2Enabled()).thenReturn(true);

        // 注册表 mock（空工具列表即可），供 buildToolSpecifications 流式过滤
        AgentToolRegistry registry = mock(AgentToolRegistry.class);
        when(registry.getDefinitions()).thenReturn(List.of());

        ReflectionTestUtils.setField(facade, "chatAgentProperties", new ChatAgentProperties());
        ReflectionTestUtils.setField(facade, "chatRuntimeSettings", chatRuntimeSettings);
        ReflectionTestUtils.setField(facade, "agentToolRegistry", registry);
        ReflectionTestUtils.setField(facade, "agentToolGateway", agentToolGateway);
        ReflectionTestUtils.setField(facade, "chatConversationSupport", conversationSupport);
        ReflectionTestUtils.setField(facade, "chatMessageService", chatMessageService);
        ReflectionTestUtils.setField(facade, "chatConversationService", chatConversationService);
        ReflectionTestUtils.setField(facade, "userService", userService);
        ReflectionTestUtils.setField(facade, "aiUsageLogService", mock(AiUsageLogService.class));
        ReflectionTestUtils.setField(facade, "integrationOpenAiModelFactory", mock(IntegrationOpenAiModelFactory.class));
    }

    @Test
    void confirmTurn_executesPendingAndStreamsResult() {
        ConfirmedExecution execution = new ConfirmedExecution(
                "publish_blog_post",
                "{\"id\":42}",
                "将发布文章《Spring 入门》",
                com.ai.agent.model.AgentToolResult.ok(
                        AgentToolRegistryBuilder.mapOf("postId", 42L, "status", 1)));
        when(agentToolGateway.executeConfirmed(eq("cfm_abc"), any(AgentToolContext.class)))
                .thenReturn(execution);

        List<ChatStreamEvent> events = facade.chat(10L, null, "确认发布", null,
                mock(HttpServletRequest.class), "cfm_abc").collectList().block();

        // 确认续跑：跳过 LLM，直接 tool_call → tool_result → chunk → done
        assertEquals(4, events.size());
        assertEquals(ChatStreamEvent.EVENT_TOOL_CALL, events.get(0).getEvent());
        assertEquals("publish_blog_post", events.get(0).getTool());
        assertEquals(ChatStreamEvent.EVENT_TOOL_RESULT, events.get(1).getEvent());
        assertTrue(events.get(1).getSuccess());
        assertEquals(ChatStreamEvent.EVENT_CHUNK, events.get(2).getEvent());
        assertTrue(events.get(2).getD().contains("将发布文章《Spring 入门》"));
        assertEquals(ChatStreamEvent.EVENT_DONE, events.get(3).getEvent());

        // 确认后的执行结果落库为 AI 消息
        verify(chatMessageService).addMessage(eq(10L), eq(1L), eq(MessageTypeEnum.AI.getValue()),
                contains("将发布文章《Spring 入门》"), any());
        // 用户消息与 AI 回复各刷新一次会话时间
        verify(chatConversationService, times(2)).touchLastMessageAt(10L);
    }

    @Test
    void confirmTurn_invalidTokenEmitsErrorAndDone() {
        when(agentToolGateway.executeConfirmed(anyString(), any(AgentToolContext.class)))
                .thenThrow(new com.ai.exception.BusinessException(
                        com.ai.exception.ErrorCode.PARAMS_ERROR, "确认已过期或无效，请重新发起指令"));

        List<ChatStreamEvent> events = facade.chat(10L, null, "确认发布", null,
                mock(HttpServletRequest.class), "cfm_bad").collectList().block();

        assertEquals(2, events.size());
        assertEquals(ChatStreamEvent.EVENT_ERROR, events.get(0).getEvent());
        assertEquals("确认已过期或无效，请重新发起指令", events.get(0).getMessage());
        assertEquals(ChatStreamEvent.EVENT_DONE, events.get(1).getEvent());
    }

    @Test
    void l2ToolCallSuspendsWithConfirmRequiredEvent() throws Exception {
        // 模型第一轮返回工具调用请求；网关判定 L2 需确认抛出 ConfirmRequiredException
        ChatModel chatModel = mock(ChatModel.class);
        ChatResponse response = mock(ChatResponse.class);
        AiMessage aiMessage = mock(AiMessage.class);
        when(aiMessage.hasToolExecutionRequests()).thenReturn(true);
        when(aiMessage.toolExecutionRequests()).thenReturn(List.of(ToolExecutionRequest.builder()
                .id("call_1")
                .name("publish_blog_post")
                .arguments("{\"id\":42}")
                .build()));
        when(response.aiMessage()).thenReturn(aiMessage);
        when(chatModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class))).thenReturn(response);
        IntegrationOpenAiModelFactory modelFactory = mock(IntegrationOpenAiModelFactory.class);
        when(modelFactory.agentChatModel()).thenReturn(chatModel);
        ReflectionTestUtils.setField(facade, "integrationOpenAiModelFactory", modelFactory);

        when(agentToolGateway.execute(eq("publish_blog_post"), eq("{\"id\":42}"), any(AgentToolContext.class)))
                .thenThrow(new ConfirmRequiredException("cfm_8f3a", "publish_blog_post", "将发布文章《Spring 入门》"));

        List<ChatStreamEvent> events = facade.chat(10L, null, "发布这篇博客", null,
                mock(HttpServletRequest.class), null).collectList().block();

        // tool_call → confirm_required（带令牌与 TTL）→ done，本轮暂停且工具未执行
        assertEquals(3, events.size());
        assertEquals(ChatStreamEvent.EVENT_TOOL_CALL, events.get(0).getEvent());
        assertEquals(ChatStreamEvent.EVENT_CONFIRM_REQUIRED, events.get(1).getEvent());
        assertEquals("cfm_8f3a", events.get(1).getConfirmToken());
        assertEquals("publish_blog_post", events.get(1).getTool());
        assertEquals("将发布文章《Spring 入门》", events.get(1).getSummary());
        assertEquals(300, events.get(1).getExpiresInSec());
        assertEquals(SSE_TYPE, events.get(1).getType());
        assertEquals(ChatStreamEvent.EVENT_DONE, events.get(2).getEvent());

        // 暂停提示落库为 AI 消息
        verify(chatMessageService).addMessage(eq(10L), eq(1L), eq(MessageTypeEnum.AI.getValue()),
                contains("将发布文章《Spring 入门》"), any());
    }
}
