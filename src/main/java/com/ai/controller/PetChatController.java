package com.ai.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.ai.agent.ChatOrchestrator;
import com.ai.common.BaseResponse;
import com.ai.common.ResultUtils;
import com.ai.config.ConditionalOnModule;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.model.dto.chat.ChatConversationResolveRequest;
import com.ai.model.dto.chat.ChatRequest;
import com.ai.model.entity.User;
import com.ai.model.vo.chat.ChatConfigVO;
import com.ai.model.vo.chat.ChatConversationVO;
import com.ai.model.vo.chat.ChatMessageVO;
import com.ai.model.vo.chat.ChatStreamEvent;
import com.ai.service.AstrBotChatService;
import com.ai.service.ChatConversationService;
import com.ai.service.ChatMessageService;
import com.ai.service.UserService;
import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * EchoBot 聊天桥接：复用现有 AstrBot / 会话能力，鉴权走 Device Token。
 */
@ConditionalOnModule("chat")
@RestController
@RequestMapping("/pet")
public class PetChatController {

    @Resource
    private ChatOrchestrator chatOrchestrator;

    @Resource
    private AstrBotChatService astrBotChatService;

    @Resource
    private ChatConversationService chatConversationService;

    @Resource
    private ChatMessageService chatMessageService;

    @Resource
    private UserService userService;

    @GetMapping("/chat/configs")
    public BaseResponse<List<ChatConfigVO>> configs(HttpServletRequest request) {
        requireLogin(request);
        return ResultUtils.success(astrBotChatService.listConfigs());
    }

    @PostMapping("/chat/conversations/resolve")
    public BaseResponse<ChatConversationVO> resolve(@RequestBody ChatConversationResolveRequest body,
                                                     HttpServletRequest request) {
        User user = requireLogin(request);
        if (body == null || StrUtil.isBlank(body.getConfigId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "configId 不能为空");
        }
        return ResultUtils.success(chatConversationService.resolveDefault(user.getId(), body.getConfigId()));
    }

    @GetMapping("/chat/conversations/{id}/messages")
    public BaseResponse<Page<ChatMessageVO>> messages(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        User user = requireLogin(request);
        return ResultUtils.success(chatMessageService.listByConversation(id, user.getId(), pageNum, pageSize));
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@RequestBody ChatRequest chatRequest, HttpServletRequest request) {
        requireLogin(request);
        if (chatRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求体不能为空");
        }
        boolean hasText = StrUtil.isNotBlank(chatRequest.getMessage());
        boolean hasSegments = chatRequest.getSegments() != null && !chatRequest.getSegments().isEmpty();
        if (!hasText && !hasSegments) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "消息不能为空");
        }
        Long conversationId = chatRequest.getConversationId();
        String configId = chatRequest.getConfigId();
        boolean hasConversationId = conversationId != null && conversationId > 0;
        boolean hasConfigId = StrUtil.isNotBlank(configId);
        if (!hasConversationId && !hasConfigId) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "conversationId 与 configId 至少传一个");
        }
        // 桌宠默认 Ask，避免误开 Agent
        if (StrUtil.isBlank(chatRequest.getMode())) {
            chatRequest.setMode("ask");
        }
        Flux<ChatStreamEvent> eventFlux = chatOrchestrator.chat(chatRequest, request);
        return eventFlux.map(event -> ServerSentEvent.<String>builder()
                .data(JSONUtil.toJsonStr(event))
                .build());
    }

    private User requireLogin(HttpServletRequest request) {
        return userService.getLoginUser(request);
    }
}
