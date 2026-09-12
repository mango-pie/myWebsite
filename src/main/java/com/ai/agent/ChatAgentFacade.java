package com.ai.agent;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.ai.agent.config.ChatAgentProperties;
import com.ai.agent.model.AgentToolContext;
import com.ai.agent.model.AgentToolResult;
import com.ai.agent.registry.AgentToolGateway;
import com.ai.agent.registry.AgentToolRegistry;
import com.ai.agent.support.ChatConversationSupport;
import com.ai.model.dto.chat.ChatMessageSegment;
import com.ai.model.entity.ChatConversation;
import com.ai.model.entity.User;
import com.ai.model.enums.ChatMessageSourceEnum;
import com.ai.model.enums.MessageTypeEnum;
import com.ai.model.vo.chat.ChatMessageVO;
import com.ai.model.vo.chat.ChatStreamEvent;
import com.ai.service.ChatConversationService;
import com.ai.service.ChatMessageService;
import com.ai.service.UserService;
import com.ai.utils.ChatMessageUtils;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerErrorException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ChatAgentFacade {

    private static final String SSE_TYPE = "agent";

    @Resource
    private ChatAgentProperties chatAgentProperties;

    @Resource
    private AgentToolRegistry agentToolRegistry;

    @Resource
    private AgentToolGateway agentToolGateway;

    @Resource
    private ChatConversationSupport chatConversationSupport;

    @Resource
    private ChatMessageService chatMessageService;

    @Resource
    private ChatConversationService chatConversationService;

    @Resource
    private UserService userService;

    @Autowired(required = false)
    @Qualifier("agentChatModel")
    private ChatModel agentChatModel;

    public Flux<ChatStreamEvent> chat(Long conversationId, String configId, String message,
                                      List<ChatMessageSegment> segments, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        if (loginUser == null) {
            throw new ServerErrorException("用户未登录", null);
        }
        if (segments != null && !segments.isEmpty()) {
            throw new ServerErrorException("Agent 模式暂不支持富消息段，请使用纯文本 message", null);
        }
        if (agentChatModel == null) {
            throw new ServerErrorException("Agent 模型未配置，请检查 chat.agent.enabled 与 langchain4j.open-ai.agent-chat-model", null);
        }

        ChatConversation conversation = chatConversationSupport.resolveConversation(
                loginUser.getId(), conversationId, configId);
        Long effectiveConversationId = conversation.getId();
        String persistContent = ChatMessageUtils.extractPersistContent(message, segments);
        if (StrUtil.isBlank(persistContent)) {
            throw new ServerErrorException("消息不能为空", null);
        }

        chatMessageService.addMessage(
                effectiveConversationId,
                loginUser.getId(),
                MessageTypeEnum.USER.getValue(),
                persistContent,
                ChatMessageSourceEnum.NORMAL.getValue()
        );
        chatConversationService.touchLastMessageAt(effectiveConversationId);

        AgentToolContext toolContext = AgentToolContext.builder()
                .userId(loginUser.getId())
                .conversationId(effectiveConversationId)
                .configId(conversation.getConfigId())
                .request(request)
                .build();

        return Flux.<ChatStreamEvent>create(sink -> runAgentLoop(
                sink, toolContext, persistContent, effectiveConversationId, loginUser.getId()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private void runAgentLoop(FluxSink<ChatStreamEvent> sink, AgentToolContext toolContext,
                              String userMessage, Long conversationId, Long userId) {
        try {
            List<ChatMessage> messages = buildInitialMessages(conversationId, userId, userMessage);
            List<ToolSpecification> toolSpecifications = agentToolRegistry.toolSpecifications();
            int step = 0;
            String finalText = null;

            while (step < chatAgentProperties.getMaxSteps()) {
                ChatResponse response = agentChatModel.chat(ChatRequest.builder()
                        .messages(messages)
                        .toolSpecifications(toolSpecifications)
                        .build());
                AiMessage aiMessage = response.aiMessage();
                if (aiMessage == null) {
                    sink.error(new ServerErrorException("Agent 模型返回为空", null));
                    return;
                }

                if (aiMessage.hasToolExecutionRequests()) {
                    messages.add(aiMessage);
                    for (ToolExecutionRequest toolRequest : aiMessage.toolExecutionRequests()) {
                        step++;
                        Map<String, Object> argsMap = parseToolArgs(toolRequest.arguments());
                        sink.next(ChatStreamEvent.toolCall(toolRequest.name(), argsMap, step, SSE_TYPE));

                        AgentToolResult toolResult = agentToolGateway.execute(
                                toolRequest.name(), toolRequest.arguments(), toolContext);
                        sink.next(ChatStreamEvent.toolResult(
                                toolRequest.name(),
                                toolResult.isSuccess(),
                                toolResult.getData(),
                                toolResult.getUiAction(),
                                step,
                                SSE_TYPE
                        ));

                        messages.add(ToolExecutionResultMessage.from(toolRequest, toolResult.toJsonForLlm()));
                    }
                    continue;
                }

                finalText = aiMessage.text();
                break;
            }

            if (finalText == null) {
                sink.next(ChatStreamEvent.error("Agent 步骤过多，请拆分指令后重试", SSE_TYPE));
                persistError(conversationId, userId, "Agent 步骤过多");
            } else if (StrUtil.isNotBlank(finalText)) {
                sink.next(ChatStreamEvent.chunk(finalText, SSE_TYPE));
                chatMessageService.addMessage(
                        conversationId,
                        userId,
                        MessageTypeEnum.AI.getValue(),
                        finalText.trim(),
                        ChatMessageSourceEnum.AGENT.getValue()
                );
                chatConversationService.touchLastMessageAt(conversationId);
            }
            sink.next(ChatStreamEvent.done(SSE_TYPE));
            sink.complete();
        } catch (Exception e) {
            log.error("Agent loop failed", e);
            sink.next(ChatStreamEvent.error("AI回复失败: " + e.getMessage(), SSE_TYPE));
            persistError(conversationId, userId, "AI回复失败: " + e.getMessage());
            sink.next(ChatStreamEvent.done(SSE_TYPE));
            sink.complete();
        }
    }

    private List<ChatMessage> buildInitialMessages(Long conversationId, Long userId, String userMessage) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(chatAgentProperties.getSystemPrompt()));
        List<ChatMessageVO> history = chatMessageService.listLatest(
                conversationId, userId, chatAgentProperties.getHistoryLimit());
        for (ChatMessageVO item : history) {
            if (MessageTypeEnum.USER.getValue().equals(item.getMessageType())
                    && StrUtil.isNotBlank(item.getContent())) {
                messages.add(UserMessage.from(item.getContent()));
            } else if (MessageTypeEnum.AI.getValue().equals(item.getMessageType())
                    && StrUtil.isNotBlank(item.getContent())) {
                messages.add(AiMessage.from(item.getContent()));
            }
        }
        if (messages.size() == 1 || !(messages.get(messages.size() - 1) instanceof UserMessage)) {
            messages.add(UserMessage.from(userMessage));
        }
        return messages;
    }

    private Map<String, Object> parseToolArgs(String arguments) {
        if (StrUtil.isBlank(arguments)) {
            return Map.of();
        }
        try {
            if (JSONUtil.isTypeJSON(arguments)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = JSONUtil.toBean(arguments, Map.class);
                return map == null ? Map.of() : new LinkedHashMap<>(map);
            }
        } catch (Exception ignore) {
        }
        return Map.of("raw", arguments);
    }

    private void persistError(Long conversationId, Long userId, String errorMessage) {
        chatMessageService.addMessage(
                conversationId,
                userId,
                MessageTypeEnum.ERROR.getValue(),
                errorMessage,
                ChatMessageSourceEnum.AGENT.getValue()
        );
        chatConversationService.touchLastMessageAt(conversationId);
    }
}
