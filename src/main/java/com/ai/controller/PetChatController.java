package com.ai.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
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
import com.ai.service.UserService;
import com.ai.service.pet.PetFacade;
import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
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

import java.util.List;

/**
 * Pet 聊天能力接口。仅在 chat 模块启用时注册。
 * <p>
 * 鉴权走 {@link UserService#getLoginUser} 解析出的身份（当前为 Session）。
 * 后续 Device Bearer 过滤器可写入同一登录上下文，本 Controller 无需改 Session 判断。
 * 设备绑定管理（仅 Session）不在本 PR 范围。
 */
@ConditionalOnModule("chat")
@RestController
@RequestMapping("/pet")
public class PetChatController {

    @Resource
    private PetFacade petFacade;

    @Resource
    private UserService userService;

    @GetMapping("/chat/configs")
    public BaseResponse<List<ChatConfigVO>> getConfigs(HttpServletRequest request) {
        userService.getLoginUser(request);
        return ResultUtils.success(petFacade.chatConfigs());
    }

    @PostMapping("/chat/conversations/resolve")
    public BaseResponse<ChatConversationVO> resolveDefault(@RequestBody ChatConversationResolveRequest resolveRequest,
                                                           HttpServletRequest request) {
        if (resolveRequest == null || StrUtil.isBlank(resolveRequest.getConfigId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "configId 不能为空");
        }
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(petFacade.chatResolve(loginUser.getId(), resolveRequest.getConfigId()));
    }

    @GetMapping("/chat/conversations/{id}/messages")
    public BaseResponse<Page<ChatMessageVO>> listMessages(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(petFacade.chatMessages(id, loginUser.getId(), pageNum, pageSize));
    }

    /**
     * Pet 角色聊天（POST + body）。SSE 事件与网站 {@code /chat/chat} 一致。
     * 未传 mode 时默认 {@code ask}；需传 conversationId 或 configId。
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@RequestBody ChatRequest chatRequest,
                                              HttpServletRequest request) {
        userService.getLoginUser(request);
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

        if (StrUtil.isBlank(chatRequest.getMode())) {
            chatRequest.setMode("ask");
        }

        Flux<ChatStreamEvent> eventFlux = petFacade.chatStream(chatRequest, request);
        return eventFlux.map(event -> ServerSentEvent.<String>builder()
                .data(JSONUtil.toJsonStr(event))
                .build());
    }
}
