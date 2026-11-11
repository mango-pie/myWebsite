package com.ai.agent;

import com.ai.config.ConditionalOnModule;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.ai.agent.config.ChatAgentProperties;
import com.ai.agent.confirm.ConfirmRequiredException;
import com.ai.agent.model.AgentToolContext;
import com.ai.agent.model.AgentToolLevel;
import com.ai.agent.model.AgentToolResult;
import com.ai.agent.registry.AgentToolDefinition;
import com.ai.agent.registry.AgentToolGateway;
import com.ai.agent.registry.AgentToolRegistry;
import com.ai.agent.registry.ConfirmedExecution;
import com.ai.agent.support.ChatConversationSupport;
import com.ai.constant.AiUsageSceneConstant;
import com.ai.exception.BusinessException;
import com.ai.model.dto.chat.ChatMessageSegment;
import com.ai.model.dto.ops.AiUsageRecord;
import com.ai.model.entity.ChatConversation;
import com.ai.model.entity.User;
import com.ai.model.enums.ChatMessageSourceEnum;
import com.ai.model.enums.MessageTypeEnum;
import com.ai.model.vo.chat.ChatMessageVO;
import com.ai.model.vo.chat.ChatStreamEvent;
import com.ai.service.AiUsageLogService;
import com.ai.service.ChatConversationService;
import com.ai.service.ChatMessageService;
import com.ai.service.UserService;
import com.ai.utils.AiUsageTokenExtractor;
import com.ai.service.chat.support.ChatMessageUtils;
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

/**
 * Agent 模式聊天门面：以「模型 ↔ 工具」循环驱动多步任务。
 *
 * <p>每轮将系统提示词、最近会话历史和全部工具规格发给模型；模型返回工具调用时，
 * 交给 {@link AgentToolGateway} 执行，并把 tool_call / tool_result 以 SSE 事件推给前端，
 * 将工具结果回填到消息列表继续下一轮，直到模型产出最终文本或达到 agentMaxSteps 步数上限。
 * L2 工具触发时推送 confirm_required 暂停本轮（设计文档 §8.3），用户确认后带
 * confirmToken 重新进入，走确认续跑（跳过 LLM）执行挂起操作。
 * 用户消息、最终回复与错误都会落库，每次模型调用记录 AI 用量。
 */
@ConditionalOnModule("chat")
@Service
@Slf4j
public class ChatAgentFacade {

    private static final String SSE_TYPE = "agent";

    @Resource
    private ChatAgentProperties chatAgentProperties;

    @Resource
    private com.ai.setting.runtime.ChatRuntimeSettings chatRuntimeSettings;

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

    @Resource
    private AiUsageLogService aiUsageLogService;

    @Autowired(required = false)
    @Qualifier("agentChatModel")
    private ChatModel agentChatModel;

    @Resource
    private com.ai.setting.IntegrationOpenAiModelFactory integrationOpenAiModelFactory;

    /**
     * Agent 模式入口：校验登录与纯文本输入，解析/创建会话并落库用户消息，
     * 然后在 boundedElastic 线程上启动 Agent 循环，以 SSE 流返回过程事件与最终回复。
     *
     * <p>confirmToken 非空时为「确认续跑」（设计文档 §8.3）：跳过 LLM，
     * 直接消费挂起的 L2 操作并执行，推送 tool_call / tool_result / chunk / done。
     */
    public Flux<ChatStreamEvent> chat(Long conversationId, String configId, String message,
                                      List<ChatMessageSegment> segments, HttpServletRequest request,
                                      String confirmToken) {
        User loginUser = userService.getLoginUser(request);
        if (loginUser == null) {
            throw new ServerErrorException("用户未登录", null);
        }
        if (segments != null && !segments.isEmpty()) {
            throw new ServerErrorException("Agent 模式暂不支持富消息段，请使用纯文本 message", null);
        }
        boolean confirmTurn = StrUtil.isNotBlank(confirmToken);
        ChatModel chatModel = null;
        if (!confirmTurn) {
            chatModel = resolveAgentChatModel();
            if (chatModel == null) {
                throw new ServerErrorException("Agent 模型未配置，请检查 chat.agent.enabled 与集成设置 / langchain4j.open-ai.agent-chat-model", null);
            }
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

        if (confirmTurn) {
            return Flux.<ChatStreamEvent>create(sink -> runConfirmTurn(
                            sink, toolContext, confirmToken, effectiveConversationId, loginUser.getId()))
                    .subscribeOn(Schedulers.boundedElastic());
        }
        ChatModel modelForLoop = chatModel;
        return Flux.<ChatStreamEvent>create(sink -> runAgentLoop(
                        sink, toolContext, persistContent, effectiveConversationId, loginUser.getId(), modelForLoop))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 解析 Agent 使用的模型：优先取运营在集成设置里配置的模型，
     * 未配置或构建失败时回退到本地 langchain4j 配置的 agentChatModel Bean。
     */
    private ChatModel resolveAgentChatModel() {
        try {
            return integrationOpenAiModelFactory.agentChatModel();
        } catch (Exception e) {
            return agentChatModel;
        }
    }

    /**
     * Agent 核心循环（阻塞执行，运行在 boundedElastic 调度器上）。
     *
     * <p>循环体：调用模型 → 若带工具执行请求则逐个经网关执行、推送 SSE 事件、
     * 回填 {@code ToolExecutionResultMessage} 后继续下一轮；否则视为最终文本跳出。
     * 超过最大步数或模型返回空时向流中写 error 事件并持久化错误消息。
     * 任何异常都不让流中断，统一转为 error + done 事件正常收尾。
     */
    private void runAgentLoop(FluxSink<ChatStreamEvent> sink, AgentToolContext toolContext,
                              String userMessage, Long conversationId, Long userId, ChatModel chatModel) {
        try {
            List<ChatMessage> messages = buildInitialMessages(conversationId, userId, userMessage);
            List<ToolSpecification> toolSpecifications = buildToolSpecifications();
            int step = 0;
            String finalText = null;

            while (step < chatRuntimeSettings.agentMaxSteps()) {
                long startNs = System.nanoTime();
                ChatResponse response;
                try {
                    response = chatModel.chat(ChatRequest.builder()
                            .messages(messages)
                            .toolSpecifications(toolSpecifications)
                            .build());
                    recordAgentUsage(userId, conversationId, true, startNs, response, null);
                } catch (Exception e) {
                    recordAgentUsage(userId, conversationId, false, startNs, null, e.getMessage());
                    throw e;
                }
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

                        AgentToolResult toolResult;
                        try {
                            toolResult = agentToolGateway.execute(
                                    toolRequest.name(), toolRequest.arguments(), toolContext);
                        } catch (ConfirmRequiredException confirmEx) {
                            // L2 工具待用户确认（设计文档 §8.3）：推送 confirm_required 后暂停本轮，
                            // 工具未执行；用户确认后带 confirmToken 重新进入，经确认续跑执行。
                            sink.next(ChatStreamEvent.confirmRequired(
                                    confirmEx.getConfirmToken(),
                                    confirmEx.getToolName(),
                                    confirmEx.getSummary(),
                                    chatAgentProperties.getL2ConfirmTtlSeconds(),
                                    SSE_TYPE
                            ));
                            persistAssistantMessage(conversationId, userId,
                                    "已发起需确认的操作：" + confirmEx.getSummary()
                                            + "，请在确认卡片中确认或取消，确认后我会继续执行。");
                            sink.next(ChatStreamEvent.done(SSE_TYPE));
                            sink.complete();
                            return;
                        }
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
                persistAssistantMessage(conversationId, userId, finalText.trim());
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

    /**
     * 确认续跑（设计文档 §8.3）：用户确认后带 confirmToken 进入，跳过 LLM，
     * 由网关一次性消费令牌并按挂起参数重放 L2 工具，随后依次推送
     * tool_call / tool_result / chunk（确定性结果说明，不经过模型）/ done。
     * 令牌无效 / 过期 / 非本人 / 会话不一致时以 error 事件收尾，不会执行任何工具。
     */
    private void runConfirmTurn(FluxSink<ChatStreamEvent> sink, AgentToolContext toolContext,
                                String confirmToken, Long conversationId, Long userId) {
        try {
            ConfirmedExecution execution = agentToolGateway.executeConfirmed(confirmToken, toolContext);
            int step = 1;
            sink.next(ChatStreamEvent.toolCall(
                    execution.toolName(), parseToolArgs(execution.toolArgs()), step, SSE_TYPE));
            AgentToolResult result = execution.result();
            sink.next(ChatStreamEvent.toolResult(
                    execution.toolName(),
                    result.isSuccess(),
                    result.getData(),
                    result.getUiAction(),
                    step,
                    SSE_TYPE
            ));
            String finalText = buildConfirmResultText(execution);
            sink.next(ChatStreamEvent.chunk(finalText, SSE_TYPE));
            persistAssistantMessage(conversationId, userId, finalText);
            sink.next(ChatStreamEvent.done(SSE_TYPE));
            sink.complete();
        } catch (BusinessException e) {
            log.warn("Agent confirm turn rejected: userId={}, message={}", userId, e.getMessage());
            sink.next(ChatStreamEvent.error(e.getMessage(), SSE_TYPE));
            persistError(conversationId, userId, e.getMessage());
            sink.next(ChatStreamEvent.done(SSE_TYPE));
            sink.complete();
        } catch (Exception e) {
            log.error("Agent confirm turn failed", e);
            sink.next(ChatStreamEvent.error("确认执行失败: " + e.getMessage(), SSE_TYPE));
            persistError(conversationId, userId, "确认执行失败: " + e.getMessage());
            sink.next(ChatStreamEvent.done(SSE_TYPE));
            sink.complete();
        }
    }

    /** 确认续跑的最终回复：确定性文案，不经过模型（设计文档 §5.1「跳过 LLM」）。 */
    private String buildConfirmResultText(ConfirmedExecution execution) {
        if (execution.result().isSuccess()) {
            return "已按你的确认执行：" + execution.summary() + "，操作完成。";
        }
        return "已按你的确认执行：" + execution.summary() + "，但执行失败：" + execution.result().getError();
    }

    /** 落库一条 AI 消息（AGENT 来源）并刷新会话最后消息时间。 */
    private void persistAssistantMessage(Long conversationId, Long userId, String content) {
        chatMessageService.addMessage(
                conversationId,
                userId,
                MessageTypeEnum.AI.getValue(),
                content,
                ChatMessageSourceEnum.AGENT.getValue()
        );
        chatConversationService.touchLastMessageAt(conversationId);
    }

    /**
     * 提供给模型的工具签名：L2 工具仅在确认流开启（agentL2Enabled）时暴露，
     * 关闭时模型看不到 L2 工具，避免必然失败的调用（网关侧仍有兜底拦截）。
     */
    private List<ToolSpecification> buildToolSpecifications() {
        boolean l2Enabled = chatRuntimeSettings.agentL2Enabled();
        return agentToolRegistry.getDefinitions().stream()
                .filter(definition -> l2Enabled || definition.level() != AgentToolLevel.L2)
                .map(AgentToolDefinition::specification)
                .toList();
    }

    /**
     * 组装首轮消息：系统提示词 + 最近 agentHistoryLimit 条历史（仅保留 user/ai 文本）。
     * 历史末条不是用户消息（如上次以错误收尾）时，补上本轮用户输入保证对话连贯。
     */
    private List<ChatMessage> buildInitialMessages(Long conversationId, Long userId, String userMessage) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(chatAgentProperties.getSystemPrompt()));
        List<ChatMessageVO> history = chatMessageService.listLatest(
                conversationId, userId, chatRuntimeSettings.agentHistoryLimit());
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

    /**
     * 把模型的工具参数 JSON 解析成 Map 仅用于 SSE 展示；模型可能输出非法 JSON，
     * 此时兜底为 {@code {"raw": 原文}}，真正的执行解析在工具模块内完成。
     */
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

    /** 记录一次模型调用的 token 用量与耗时（scene=agent），成功失败各记一条。 */
    private void recordAgentUsage(Long userId, Long conversationId, boolean success, long startNs,
                                  ChatResponse response, String errorMessage) {
        AiUsageTokenExtractor.Tokens tokens = AiUsageTokenExtractor.from(response);
        long elapsedMs = Math.max(0L, (System.nanoTime() - startNs) / 1_000_000L);
        aiUsageLogService.record(AiUsageRecord.builder()
                .userId(userId)
                .scene(AiUsageSceneConstant.AGENT)
                .conversationId(conversationId)
                .modelName("agent")
                .promptTokens(tokens.promptTokens())
                .completionTokens(tokens.completionTokens())
                .totalTokens(tokens.totalTokens())
                .responseTimeMs(elapsedMs)
                .status(success ? AiUsageSceneConstant.STATUS_SUCCESS : AiUsageSceneConstant.STATUS_ERROR)
                .errorMessage(errorMessage)
                .requestSummary("agent step")
                .build());
    }
}
