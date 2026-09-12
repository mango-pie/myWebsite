package com.ai.agent;

import com.ai.core.AiChatFacade;
import com.ai.model.dto.chat.ChatMessageSegment;
import com.ai.model.dto.chat.ChatRequest;
import com.ai.model.vo.chat.ChatStreamEvent;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
public class ChatOrchestrator {

    @Resource
    private AiChatFacade aiChatFacade;

    @Resource
    private ChatAgentFacade chatAgentFacade;

    @Resource
    private com.ai.agent.config.ChatAgentProperties chatAgentProperties;

    public Flux<ChatStreamEvent> chat(ChatRequest chatRequest, HttpServletRequest request) {
        ChatMode mode = ChatMode.from(chatRequest.getMode());
        Long conversationId = chatRequest.getConversationId();
        String configId = chatRequest.getConfigId();
        String message = chatRequest.getMessage();
        List<ChatMessageSegment> segments = chatRequest.getSegments();

        if (mode == ChatMode.AGENT) {
            chatAgentProperties.requireEnabled();
            return chatAgentFacade.chat(conversationId, configId, message, segments, request);
        }
        return aiChatFacade.chat(conversationId, configId, message, segments, request);
    }
}
