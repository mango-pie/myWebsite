package com.ai.core;

import cn.hutool.core.util.StrUtil;
import com.ai.config.ChatSegmentationProperties;
import com.ai.model.dto.chat.ChatMessageSegment;
import com.ai.model.entity.ChatConversation;
import com.ai.model.entity.User;
import com.ai.model.enums.ChatMessageSourceEnum;
import com.ai.model.enums.MessageTypeEnum;
import com.ai.model.vo.chat.ChatConversationVO;
import com.ai.model.vo.chat.ChatStreamEvent;
import com.ai.service.AstrBotChatService;
import com.ai.service.ChatAttachmentService;
import com.ai.service.ChatConversationService;
import com.ai.service.ChatMessageService;
import com.ai.service.ChatSegmentationService;
import com.ai.service.UserService;
import com.ai.utils.ChatMessageUtils;
import com.ai.utils.ChatSegmentationUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerErrorException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@Service
@Slf4j
public class AiChatFacade {

    @Resource
    private AstrBotChatService astrBotChatService;

    @Resource
    private ChatMessageService chatMessageService;

    @Resource
    private ChatConversationService chatConversationService;

    @Resource
    private UserService userService;

    @Resource
    private ChatSegmentationService chatSegmentationService;

    @Resource
    private ChatSegmentationProperties chatSegmentationProperties;

    @Resource
    private ChatAttachmentService chatAttachmentService;

    /**
     * 角色聊天入口（AstrBot 平台预设驱动，与代码生成 App 彻底分离）。
     * 流结束后可选智能分段，通过 segment_plan SSE 事件通知前端。
     */
    public Flux<ChatStreamEvent> chat(Long conversationId, String configId, String message,
                                      List<ChatMessageSegment> segments, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        if (loginUser == null) {
            throw new ServerErrorException("用户未登录", null);
        }

        ChatConversation conversation = resolveConversation(loginUser.getId(), conversationId, configId);
        Long effectiveConversationId = conversation.getId();
        String effectiveConfigId = conversation.getConfigId();
        String sessionId = conversation.getAstrbotSessionId();
        String sseType = effectiveConfigId;

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

        String username = "user_" + loginUser.getId();
        String astrBotMessage = message;
        List<ChatMessageSegment> astrBotSegments = segments;
        if (chatAttachmentService.hasImageSegments(segments)) {
            astrBotMessage = chatAttachmentService.buildPlainMessageForAstrBot(message, segments);
            astrBotSegments = null;
        }
        Flux<String> contentFlux = astrBotChatService.streamChat(
                username, sessionId, effectiveConfigId, astrBotMessage, astrBotSegments);

        StringBuilder aiResponseBuilder = new StringBuilder();
        Flux<ChatStreamEvent> chunkFlux = contentFlux
                .doOnNext(chunk -> aiResponseBuilder.append(chunk))
                .map(chunk -> ChatStreamEvent.chunk(chunk, sseType))
                .doOnComplete(() -> {
                    String aiResponse = astrBotChatService.cleanDisplayContent(aiResponseBuilder.toString());
                    if (aiResponse != null && !aiResponse.isBlank()) {
                        chatMessageService.addMessage(
                                effectiveConversationId,
                                loginUser.getId(),
                                MessageTypeEnum.AI.getValue(),
                                aiResponse,
                                ChatMessageSourceEnum.NORMAL.getValue()
                        );
                        chatConversationService.touchLastMessageAt(effectiveConversationId);
                    }
                })
                .doOnError(error -> {
                    String errorMessage = "AI回复失败: " + error.getMessage();
                    chatMessageService.addMessage(
                            effectiveConversationId,
                            loginUser.getId(),
                            MessageTypeEnum.ERROR.getValue(),
                            errorMessage,
                            ChatMessageSourceEnum.NORMAL.getValue()
                    );
                    chatConversationService.touchLastMessageAt(effectiveConversationId);
                });

        Flux<ChatStreamEvent> tailFlux = Flux.defer(() -> buildTailEvents(aiResponseBuilder.toString(), sseType));

        return chunkFlux.concatWith(tailFlux);
    }

    private ChatConversation resolveConversation(Long userId, Long conversationId, String configId) {
        if (conversationId != null && conversationId > 0) {
            return chatConversationService.getOwnedConversation(conversationId, userId);
        }
        if (StrUtil.isNotBlank(configId)) {
            ChatConversationVO vo = chatConversationService.resolveDefault(userId, configId);
            return chatConversationService.getOwnedConversation(vo.getId(), userId);
        }
        throw new ServerErrorException("conversationId 与 configId 至少传一个", null);
    }

    private Flux<ChatStreamEvent> buildTailEvents(String rawResponse, String sseType) {
        String cleaned = astrBotChatService.cleanDisplayContent(rawResponse);
        if (cleaned == null || cleaned.isBlank()) {
            return Flux.just(ChatStreamEvent.done(sseType));
        }
        if (!chatSegmentationProperties.isEnabled()
                || cleaned.length() < chatSegmentationProperties.getMinLength()) {
            log.debug("跳过 segment_plan: enabled={}, cleanedLength={}",
                    chatSegmentationProperties.isEnabled(), cleaned.length());
            return Flux.just(ChatStreamEvent.done(sseType));
        }

        return Mono.fromCallable(() -> chatSegmentationService.segment(cleaned))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(segments -> {
                    if (segments.size() <= 1) {
                        log.info("未下发 segment_plan: 分段结果仍为单段，cleanedLength={}", cleaned.length());
                        return Flux.just(ChatStreamEvent.done(sseType));
                    }
                    log.info("下发 segment_plan: {} 段，cleanedLength={}", segments.size(), cleaned.length());
                    List<Long> delays = ChatSegmentationUtils.buildDelaySchedule(
                            segments,
                            chatSegmentationProperties.getDelayBase(),
                            chatSegmentationProperties.getDelayPerChar(),
                            chatSegmentationProperties.getDelayMax()
                    );
                    return Flux.just(ChatStreamEvent.segmentPlan(segments, delays, sseType))
                            .concatWith(Flux.just(ChatStreamEvent.done(sseType)));
                })
                .onErrorResume(error -> Flux.just(ChatStreamEvent.done(sseType)));
    }
}
