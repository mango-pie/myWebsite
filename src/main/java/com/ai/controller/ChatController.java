package com.ai.controller;

import com.ai.config.ConditionalOnModule;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.ai.common.BaseResponse;
import com.ai.common.ResultUtils;
import com.ai.agent.ChatOrchestrator;
import com.ai.agent.confirm.ChatAgentConfirmService;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.model.dto.chat.ChatAgentConfirmCancelRequest;
import com.ai.model.dto.chat.ChatRequest;
import com.ai.model.vo.chat.ChatConfigVO;
import com.ai.model.vo.chat.ChatStreamEvent;
import com.ai.service.AstrBotChatService;
import com.ai.model.entity.User;
import com.ai.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@ConditionalOnModule("chat")
@RestController
@RequestMapping("/chat")
public class ChatController {

    @Resource
    private ChatOrchestrator chatOrchestrator;

    @Resource
    private ChatAgentConfirmService chatAgentConfirmService;

    @Resource
    private AstrBotChatService astrBotChatService;

    @Resource
    private UserService userService;

    /**
     * 角色聊天接口（POST + body，支持长文本与未来富消息段）。
     * SSE 事件：Ask → chunk（→ segment_plan）→ done；
     * Agent → tool_call / tool_result /（confirm_required）→ chunk → done；
     * mode=agent 且带 confirmToken 时为 L2 确认续跑（设计文档 §8.3）。
     * 需传 conversationId 或 configId（见 ChatRequest）。
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@RequestBody ChatRequest chatRequest,
                                              HttpServletRequest request) {
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

        String effectiveMessage = hasText ? chatRequest.getMessage() : "";
        Flux<ChatStreamEvent> eventFlux = chatOrchestrator.chat(chatRequest, request);

        return eventFlux.map(event -> ServerSentEvent.<String>builder()
                .data(JSONUtil.toJsonStr(event))
                .build());
    }

    /**
     * Agent L2 确认取消（设计文档 §8.3）：用户放弃 confirm_required 卡片对应的操作。
     * 令牌一次性作废且仅本人可取消；返回 false 表示令牌已过期/不存在/非本人。
     * 确认路径不经此端点——按设计走 POST /chat/chat（mode=agent + confirmToken）。
     */
    @PostMapping("/agent/confirm/cancel")
    public BaseResponse<Boolean> cancelAgentConfirm(@RequestBody ChatAgentConfirmCancelRequest cancelRequest,
                                                    HttpServletRequest request) {
        if (cancelRequest == null || StrUtil.isBlank(cancelRequest.getConfirmToken())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "confirmToken 不能为空");
        }
        User loginUser = userService.getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "未登录，无法取消确认");
        }
        return ResultUtils.success(
                chatAgentConfirmService.cancel(loginUser.getId(), cancelRequest.getConfirmToken()));
    }

    /**
     * 获取 AstrBot 可用预设/角色列表（桌宠按关键词挑选角色）。
     */
    @GetMapping("/configs")
    public BaseResponse<List<ChatConfigVO>> getConfigs() {
        return ResultUtils.success(astrBotChatService.listConfigs());
    }
}
