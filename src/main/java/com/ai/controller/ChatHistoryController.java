package com.ai.controller;

import com.ai.annotation.AuthCheck;
import com.ai.common.BaseResponse;
import com.ai.common.ResultUtils;
import com.ai.model.dto.chatHistory.ChatHistoryQueryRequest;
import com.ai.model.entity.ChatHistory;
import com.ai.model.entity.User;
import com.ai.model.vo.ChatHistoryVO;
import com.ai.service.ChatHistoryService;
import com.ai.service.UserService;
import com.mybatisflex.core.paginate.Page;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话历史 控制层。
 *
 */
@RestController
@RequestMapping("/chatHistory")
public class ChatHistoryController {

    @Autowired
    private ChatHistoryService chatHistoryService;

    @Autowired
    private UserService userService;

    /**
     * 保存用户消息
     *
     * @param appId 应用id
     * @param message 消息内容
     * @param request 请求
     * @return 对话历史id
     */
    @PostMapping("/userMessage")
    public BaseResponse<Long> saveUserMessage(@RequestParam Long appId, @RequestParam String message, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Long chatHistoryId = chatHistoryService.saveUserMessage(appId, loginUser.getId(), message);
        return ResultUtils.success(chatHistoryId);
    }

    /**
     * 保存AI消息
     *
     * @param appId 应用id
     * @param message 消息内容
     * @param parentId 父消息id
     * @param request 请求
     * @return 对话历史id
     */
    @PostMapping("/aiMessage")
    public BaseResponse<Long> saveAiMessage(@RequestParam Long appId, @RequestParam String message, @RequestParam Long parentId, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Long chatHistoryId = chatHistoryService.saveAiMessage(appId, loginUser.getId(), message, parentId);
        return ResultUtils.success(chatHistoryId);
    }

    /**
     * 保存错误消息
     *
     * @param appId 应用id
     * @param message 错误信息
     * @param parentId 父消息id
     * @param request 请求
     * @return 对话历史id
     */
    @PostMapping("/errorMessage")
    public BaseResponse<Long> saveErrorMessage(@RequestParam Long appId, @RequestParam String message, @RequestParam Long parentId, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Long chatHistoryId = chatHistoryService.saveErrorMessage(appId, loginUser.getId(), message, parentId);
        return ResultUtils.success(chatHistoryId);
    }

    /**
     * 分页查询应用的对话历史
     *
     * @param chatHistoryQueryRequest 查询请求
     * @param appId 应用id
     * @param request 请求
     * @return 分页结果
     */
    @GetMapping("/list")
    public BaseResponse<Page<ChatHistoryVO>> listChatHistoryByPage(ChatHistoryQueryRequest chatHistoryQueryRequest, @RequestParam Long appId, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        boolean isAdmin = userService.isAdmin(loginUser);
        Page<ChatHistoryVO> page = chatHistoryService.listChatHistoryByPage(chatHistoryQueryRequest, appId, loginUser.getId(), isAdmin);
        return ResultUtils.success(page);
    }

    /**
     * 管理员分页查询所有对话历史
     *
     * @param chatHistoryQueryRequest 查询请求
     * @param request 请求
     * @return 分页结果
     */
    @AuthCheck(mustRole = "admin")
    @GetMapping("/admin/list")
    public BaseResponse<Page<ChatHistoryVO>> listChatHistoryByPageForAdmin(ChatHistoryQueryRequest chatHistoryQueryRequest, HttpServletRequest request) {
        Page<ChatHistoryVO> page = chatHistoryService.listChatHistoryByPageForAdmin(chatHistoryQueryRequest);
        return ResultUtils.success(page);
    }

    /**
     * 获取应用的最新对话历史
     *
     * @param appId 应用id
     * @param limit 限制数量，默认10
     * @param request 请求
     * @return 对话历史列表
     */
    @GetMapping("/latest")
    public BaseResponse<List<ChatHistoryVO>> getLatestChatHistory(@RequestParam Long appId, @RequestParam(required = false, defaultValue = "10") Integer limit, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        List<ChatHistoryVO> chatHistoryVOList = chatHistoryService.getLatestChatHistory(appId, limit);
        return ResultUtils.success(chatHistoryVOList);
    }

    /**
     * 根据应用id删除对话历史
     *
     * @param appId 应用id
     * @param request 请求
     * @return 是否删除成功
     */
    @DeleteMapping("/deleteByAppId")
    public BaseResponse<Boolean> deleteByAppId(@RequestParam Long appId, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        boolean isAdmin = userService.isAdmin(loginUser);
        // 只有管理员和应用创建者可以删除对话历史
        // 这里可以添加应用创建者的校验
        boolean result = chatHistoryService.deleteByAppId(appId);
        return ResultUtils.success(result);
    }
    /**
     * 分页查询某个应用的对话历史（游标查询）
     *
     * @param appId          应用ID
     * @param pageSize       页面大小
     * @param lastCreateTime 最后一条记录的创建时间
     * @param request        请求
     * @return 对话历史分页
     */
    @GetMapping("/app/{appId}")
    public BaseResponse<Page<ChatHistory>> listAppChatHistory(@PathVariable Long appId,
                                                              @RequestParam(defaultValue = "10") int pageSize,
                                                              @RequestParam(required = false) LocalDateTime lastCreateTime,
                                                              HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Page<ChatHistory> result = chatHistoryService.listAppChatHistoryByPage(appId, pageSize, lastCreateTime, loginUser);
        return ResultUtils.success(result);
    }

}
