package com.ai.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.ai.common.BaseResponse;
import com.ai.common.ResultUtils;
import com.ai.agent.ChatAgentConfigService;
import com.ai.agent.ChatOrchestrator;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.model.dto.chat.ChatRequest;
import com.ai.model.vo.chat.ChatAgentConfigVO;
import com.ai.model.vo.chat.ChatAttachmentVO;
import com.ai.model.vo.chat.ChatConfigVO;
import com.ai.model.vo.chat.ChatStreamEvent;
import com.ai.service.AstrBotChatService;
import com.ai.service.ChatAttachmentService;
import com.ai.model.entity.User;
import com.ai.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/chat")
public class ChatController {

    @Resource
    private ChatOrchestrator chatOrchestrator;

    @Resource
    private ChatAgentConfigService chatAgentConfigService;

    @Resource
    private AstrBotChatService astrBotChatService;

    @Resource
    private ChatAttachmentService chatAttachmentService;

    @Resource
    private UserService userService;

    /**
     * 角色聊天接口（POST + body，支持长文本与未来富消息段）。
     * SSE 事件：Ask → chunk（→ segment_plan）→ done；Agent → tool_call / tool_result / chunk → done。
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

    @GetMapping("/agent/config")
    public BaseResponse<ChatAgentConfigVO> getAgentConfig() {
        return ResultUtils.success(chatAgentConfigService.getConfig());
    }

    /**
     * 聊天图片附件上传：BFF Vision 转述 + 本地 caption 缓存（不再上传 AstrBot）。
     */
    @PostMapping("/attachment")
    public BaseResponse<ChatAttachmentVO> uploadAttachment(@RequestParam("file") MultipartFile file,
                                                           HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "未登录，无法上传附件");
        }
        return ResultUtils.success(chatAttachmentService.uploadImage(file));
    }

    /**
     * 获取 AstrBot 可用预设/角色列表（替代旧的 /types，本地 PromptTypeEnum 已废弃）。
     */
    @GetMapping("/configs")
    public BaseResponse<List<ChatConfigVO>> getConfigs() {
        return ResultUtils.success(astrBotChatService.listConfigs());
    }

    /**
     * @deprecated 聊天角色已全面迁移至 AstrBot 平台预设，请使用 GET /chat/configs
     */
    @Deprecated
    @GetMapping("/types")
    public BaseResponse<String[]> getPromptTypes() {
        return ResultUtils.success(new String[0]);
    }
}
