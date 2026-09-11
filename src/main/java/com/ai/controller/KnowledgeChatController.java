package com.ai.controller;

import com.ai.config.ConditionalOnModule;

import com.ai.common.BaseResponse;
import com.ai.common.ResultUtils;
import com.ai.model.dto.knowledge.KnowledgeChatRequest;
import com.ai.model.entity.User;
import com.ai.model.vo.knowledge.KnowledgeChatResponse;
import com.ai.model.vo.knowledge.KnowledgeConversationVO;
import com.ai.model.vo.knowledge.KnowledgeMessageVO;
import com.ai.service.UserService;
import com.ai.service.knowledge.KnowledgeChatService;
import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@ConditionalOnModule("knowledge")
@RestController
@RequestMapping("/kb")
public class KnowledgeChatController {

    @Resource
    private KnowledgeChatService knowledgeChatService;

    @Resource
    private UserService userService;

    @GetMapping("/conversations")
    public BaseResponse<List<KnowledgeConversationVO>> listConversations(
            @RequestParam(required = false) Long knowledgeBaseId,
            HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(knowledgeChatService.listConversations(knowledgeBaseId, loginUser.getId()));
    }

    @DeleteMapping("/conversations/{id}")
    public BaseResponse<Boolean> deleteConversation(@PathVariable Long id, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(knowledgeChatService.deleteConversation(id, loginUser.getId()));
    }

    @GetMapping("/conversations/{id}/messages")
    public BaseResponse<Page<KnowledgeMessageVO>> listMessages(@PathVariable Long id,
                                                               @RequestParam(defaultValue = "1") int pageNum,
                                                               @RequestParam(defaultValue = "20") int pageSize,
                                                               HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(knowledgeChatService.listMessages(id, loginUser.getId(), pageNum, pageSize));
    }

    @PostMapping("/chat")
    public BaseResponse<KnowledgeChatResponse> chat(@RequestBody KnowledgeChatRequest request,
                                                    HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(knowledgeChatService.chat(request, loginUser.getId()));
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody KnowledgeChatRequest request, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return knowledgeChatService.streamChat(request, loginUser.getId());
    }
}
