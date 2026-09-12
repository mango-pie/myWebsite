package com.ai.agent;

import com.ai.agent.config.ChatAgentProperties;
import com.ai.core.AiChatFacade;
import com.ai.exception.BusinessException;
import com.ai.model.dto.chat.ChatRequest;
import com.ai.model.vo.chat.ChatStreamEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatOrchestratorTest {

    private ChatOrchestrator orchestrator;
    private AiChatFacade aiChatFacade;
    private ChatAgentFacade chatAgentFacade;
    private ChatAgentProperties chatAgentProperties;

    @BeforeEach
    void setUp() {
        orchestrator = new ChatOrchestrator();
        aiChatFacade = mock(AiChatFacade.class);
        chatAgentFacade = mock(ChatAgentFacade.class);
        chatAgentProperties = new ChatAgentProperties();
        chatAgentProperties.setEnabled(true);

        ReflectionTestUtils.setField(orchestrator, "aiChatFacade", aiChatFacade);
        ReflectionTestUtils.setField(orchestrator, "chatAgentFacade", chatAgentFacade);
        ReflectionTestUtils.setField(orchestrator, "chatAgentProperties", chatAgentProperties);
    }

    @Test
    void defaultModeRoutesToAsk() {
        ChatRequest request = new ChatRequest();
        request.setConversationId(1L);
        request.setMessage("hello");
        when(aiChatFacade.chat(any(), any(), any(), any(), any()))
                .thenReturn(Flux.just(ChatStreamEvent.done("dania")));

        orchestrator.chat(request, null).blockLast();

        verify(aiChatFacade).chat(eqLong(1L), isNull(), eqStr("hello"), isNull(), isNull());
    }

    @Test
    void agentModeRoutesToAgentFacade() {
        ChatRequest request = new ChatRequest();
        request.setConversationId(1L);
        request.setMessage("加待办");
        request.setMode("agent");
        when(chatAgentFacade.chat(any(), any(), any(), any(), any()))
                .thenReturn(Flux.just(ChatStreamEvent.done("agent")));

        ChatStreamEvent last = orchestrator.chat(request, null).blockLast();
        assertEquals("agent", last.getType());
        verify(chatAgentFacade).chat(eqLong(1L), isNull(), eqStr("加待办"), isNull(), isNull());
    }

    @Test
    void agentDisabledThrows() {
        chatAgentProperties.setEnabled(false);
        ChatRequest request = new ChatRequest();
        request.setConversationId(1L);
        request.setMessage("加待办");
        request.setMode("agent");

        assertThrows(BusinessException.class, () -> orchestrator.chat(request, null).blockFirst());
    }

    private static Long eqLong(long value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }

    private static String eqStr(String value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
