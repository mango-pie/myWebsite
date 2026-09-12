package com.ai.controller;

import cn.hutool.core.util.StrUtil;
import com.ai.common.BaseResponse;
import com.ai.common.ResultUtils;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.model.dto.chat.ChatConversationCreateRequest;
import com.ai.model.dto.chat.ChatConversationResolveRequest;
import com.ai.model.entity.User;
import com.ai.model.vo.chat.ChatConversationVO;
import com.ai.model.vo.chat.ChatMessageVO;
import com.ai.service.ChatConversationService;
import com.ai.service.ChatMessageService;
import com.ai.service.UserService;
import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/chat/conversations")
public class ChatConversationController {

    @Resource
    private ChatConversationService chatConversationService;

    @Resource
    private ChatMessageService chatMessageService;

    @Resource
    private UserService userService;

    /**
     * 获取或创建该角色下的默认会话。
     */
    @PostMapping("/resolve")
    public BaseResponse<ChatConversationVO> resolveDefault(@RequestBody ChatConversationResolveRequest request,
                                                           HttpServletRequest httpRequest) {
        if (request == null || StrUtil.isBlank(request.getConfigId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "configId 不能为空");
        }
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(chatConversationService.resolveDefault(loginUser.getId(), request.getConfigId()));
    }

    /**
     * 新建非默认会话（同角色多会话）。
     */
    @PostMapping
    public BaseResponse<ChatConversationVO> createConversation(@RequestBody ChatConversationCreateRequest request,
                                                               HttpServletRequest httpRequest) {
        if (request == null || StrUtil.isBlank(request.getConfigId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "configId 不能为空");
        }
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(chatConversationService.createConversation(
                loginUser.getId(), request.getConfigId(), request.getTitle()));
    }

    /**
     * 列出当前用户的会话，可按 configId 过滤。
     */
    @GetMapping
    public BaseResponse<List<ChatConversationVO>> listConversations(
            @RequestParam(required = false) String configId,
            HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(chatConversationService.listConversations(loginUser.getId(), configId));
    }

    /**
     * 会话详情。
     */
    @GetMapping("/{id}")
    public BaseResponse<ChatConversationVO> getConversation(@PathVariable Long id,
                                                            HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(chatConversationService.getConversationVO(id, loginUser.getId()));
    }

    /**
     * 分页查询会话消息历史。
     */
    @GetMapping("/{id}/messages")
    public BaseResponse<Page<ChatMessageVO>> listMessages(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(chatMessageService.listByConversation(id, loginUser.getId(), pageNum, pageSize));
    }

    /**
     * 软删会话。
     */
    @DeleteMapping("/{id}")
    public BaseResponse<Boolean> deleteConversation(@PathVariable Long id, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(chatConversationService.deleteConversation(id, loginUser.getId()));
    }
}
