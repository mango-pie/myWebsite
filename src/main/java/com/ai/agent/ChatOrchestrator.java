package com.ai.agent;

import com.ai.core.AiChatFacade;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.model.dto.chat.ChatMessageSegment;
import com.ai.model.dto.chat.ChatRequest;
import com.ai.model.vo.chat.ChatStreamEvent;
import com.ai.setting.runtime.ChatRuntimeSettings;
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
    private ChatRuntimeSettings chatRuntimeSettings;

    public Flux<ChatStreamEvent> chat(ChatRequest chatRequest, HttpServletRequest request) {
        ChatMode mode = ChatMode.from(chatRequest.getMode());
        Long conversationId = chatRequest.getConversationId();
        String configId = chatRequest.getConfigId();
        String message = chatRequest.getMessage();
        List<ChatMessageSegment> segments = chatRequest.getSegments();

        if (mode == ChatMode.AGENT) {
            if (!chatRuntimeSettings.agentEnabled()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "Agent 模式暂未开放");
            }
            return chatAgentFacade.chat(conversationId, configId, message, segments, request);
        }
        return aiChatFacade.chat(conversationId, configId, message, segments, request);
    }
}
