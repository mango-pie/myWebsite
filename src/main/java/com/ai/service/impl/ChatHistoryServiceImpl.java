package com.ai.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.ai.constant.UserConstant;
import com.ai.exception.ErrorCode;
import com.ai.exception.ThrowUtils;
import com.ai.model.dto.chatHistory.ChatHistoryQueryRequest;
import com.ai.model.entity.App;
import com.ai.model.entity.ChatHistory;
import com.ai.model.entity.User;
import com.ai.model.enums.MessageTypeEnum;
import com.ai.model.vo.ChatHistoryVO;
import com.ai.service.AppService;
import com.ai.service.ChatHistoryService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.ai.mapper.ChatHistoryMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 对话历史 服务层实现。
 *

 */
@Service
public class ChatHistoryServiceImpl extends ServiceImpl<ChatHistoryMapper, ChatHistory> implements ChatHistoryService {

    @Resource
    @Lazy
    private AppService appService;

    @Override
    public Long saveUserMessage(Long appId, Long userId, String message) {
        ChatHistory chatHistory = ChatHistory.builder()
                .appId(appId)
                .userId(userId)
                .message(message)
                .messageType(MessageTypeEnum.USER.getValue())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .isDelete(0)
                .build();
        save(chatHistory);
        return chatHistory.getId();
    }

    @Override
    public Long saveAiMessage(Long appId, Long userId, String message, Long parentId) {
        ChatHistory chatHistory = ChatHistory.builder()
                .appId(appId)
                .userId(userId)
                .message(message)
                .messageType(MessageTypeEnum.AI.getValue())
                .parentId(parentId)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .isDelete(0)
                .build();
        save(chatHistory);
        return chatHistory.getId();
    }

    @Override
    public Long saveErrorMessage(Long appId, Long userId, String message, Long parentId) {
        ChatHistory chatHistory = ChatHistory.builder()
                .appId(appId)
                .userId(userId)
                .message(message)
                .messageType(MessageTypeEnum.ERROR.getValue())
                .parentId(parentId)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .isDelete(0)
                .build();
        save(chatHistory);
        return chatHistory.getId();
    }

//    @Override
//    public boolean deleteByAppId(Long appId) {
//        QueryWrapper queryWrapper = new QueryWrapper();
//        queryWrapper.where(ChatHistory::getAppId).eq(appId);
//        return remove(queryWrapper);
//    }

    @Override
    public Page<ChatHistoryVO> listChatHistoryByPage(ChatHistoryQueryRequest chatHistoryQueryRequest, Long appId, Long userId, boolean isAdmin) {
        QueryWrapper queryWrapper = getQueryWrapper(chatHistoryQueryRequest);
        queryWrapper.and("appId = ?", appId);
        // 非管理员只能查看自己的对话历史
        if (!isAdmin) {
            queryWrapper.and("userId = ?", userId);
        }
        // 按创建时间降序排序
        queryWrapper.orderBy("createTime", false);
        Page<ChatHistory> page = page(new Page<>(chatHistoryQueryRequest.getPageNum(), chatHistoryQueryRequest.getPageSize()), queryWrapper);
        Page<ChatHistoryVO> resultPage = new Page<>(page.getPageNumber(), page.getPageSize(), page.getTotalRow());
        resultPage.setRecords(getChatHistoryVOList(page.getRecords()));
        return resultPage;
    }

    @Override
    public Page<ChatHistoryVO> listChatHistoryByPageForAdmin(ChatHistoryQueryRequest chatHistoryQueryRequest) {
        QueryWrapper queryWrapper = getQueryWrapper(chatHistoryQueryRequest);
        // 按创建时间降序排序
        queryWrapper.orderBy(ChatHistory::getCreateTime).desc();
        Page<ChatHistory> page = page(new Page<>(chatHistoryQueryRequest.getPageNum(), chatHistoryQueryRequest.getPageSize()), queryWrapper);
        Page<ChatHistoryVO> resultPage = new Page<>(page.getPageNumber(), page.getPageSize(), page.getTotalRow());
        resultPage.setRecords(getChatHistoryVOList(page.getRecords()));
        return resultPage;
    }

    @Override
    public List<ChatHistoryVO> getLatestChatHistory(Long appId, Integer limit) {
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.where("appId = ?", appId);
        queryWrapper.orderBy("createTime", false);
        queryWrapper.limit(limit);
        List<ChatHistory> chatHistoryList = list(queryWrapper);
        // 反转列表，使最早的消息在前
        java.util.Collections.reverse(chatHistoryList);
        return getChatHistoryVOList(chatHistoryList);
    }

    @Override
    public ChatHistoryVO getChatHistoryVO(ChatHistory chatHistory) {
        return ChatHistoryVO.fromEntity(chatHistory);
    }

    @Override
    public List<ChatHistoryVO> getChatHistoryVOList(List<ChatHistory> chatHistoryList) {
        return chatHistoryList.stream()
                .map(this::getChatHistoryVO)
                .toList();
    }

//    /**
//     * 获取查询条件
//     *
//     * @param chatHistoryQueryRequest 查询请求
//     * @return 查询条件
//     */
//    private QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest) {
//        QueryWrapper queryWrapper = new QueryWrapper();
//        if (chatHistoryQueryRequest == null) {
//            return queryWrapper;
//        }
//        // 应用id
//        if (chatHistoryQueryRequest.getAppId() != null) {
//            queryWrapper.where(ChatHistory::getAppId).eq(chatHistoryQueryRequest.getAppId());
//        }
//        // 消息类型
//        if (chatHistoryQueryRequest.getMessageType() != null) {
//            queryWrapper.where(ChatHistory::getMessageType).eq(chatHistoryQueryRequest.getMessageType());
//        }
//        // 时间范围
//        if (chatHistoryQueryRequest.getStartTime() != null) {
//            queryWrapper.where(ChatHistory::getCreateTime).ge(chatHistoryQueryRequest.getStartTime());
//        }
//        if (chatHistoryQueryRequest.getEndTime() != null) {
//            queryWrapper.where(ChatHistory::getCreateTime).le(chatHistoryQueryRequest.getEndTime());
//        }
//        return queryWrapper;
//    }
    /**
     * 获取查询包装类
     *
     * @param chatHistoryQueryRequest
     * @return
     */
    @Override
    public QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest) {
        QueryWrapper queryWrapper = QueryWrapper.create();
        if (chatHistoryQueryRequest == null) {
            return queryWrapper;
        }
        Long id = chatHistoryQueryRequest.getId();
        String message = chatHistoryQueryRequest.getMessage();
        String messageType = chatHistoryQueryRequest.getMessageType();
        Long appId = chatHistoryQueryRequest.getAppId();
        Long userId = chatHistoryQueryRequest.getUserId();
        LocalDateTime lastCreateTime = chatHistoryQueryRequest.getLastCreateTime();
        String sortField = chatHistoryQueryRequest.getSortField();
        String sortOrder = chatHistoryQueryRequest.getSortOrder();
        // 拼接查询条件
        queryWrapper.eq("id", id)
                .like("message", message)
                .eq("messageType", messageType)
                .eq("appId", appId)
                .eq("userId", userId);
        // 游标查询逻辑 - 只使用 createTime 作为游标
        if (lastCreateTime != null) {
            queryWrapper.lt("createTime", lastCreateTime);
        }
        // 排序
        if (StrUtil.isNotBlank(sortField)) {
            queryWrapper.orderBy(sortField, "ascend".equals(sortOrder));
        } else {
            // 默认按创建时间降序排列
            queryWrapper.orderBy("createTime", false);
        }
        return queryWrapper;
    }

    @Override
    public boolean addChatMessage(Long appId, String message, String messageType, Long userId) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "消息内容不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(messageType), ErrorCode.PARAMS_ERROR, "消息类型不能为空");
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR, "用户ID不能为空");
        // 验证消息类型是否有效
        MessageTypeEnum messageTypeEnum = MessageTypeEnum.getByValue(messageType);
        ThrowUtils.throwIf(messageTypeEnum == null, ErrorCode.PARAMS_ERROR, "不支持的消息类型: " + messageType);
        ChatHistory chatHistory = ChatHistory.builder()
                .appId(appId)
                .message(message)
                .messageType(messageType)
                .userId(userId)
                .build();
        return this.save(chatHistory);
    }

    @Override
    public boolean deleteByAppId(Long appId) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("appId", appId);
        return this.remove(queryWrapper);
    }


    @Override
    public Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize,
                                                      LocalDateTime lastCreateTime,
                                                      User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        ThrowUtils.throwIf(pageSize <= 0 || pageSize > 50, ErrorCode.PARAMS_ERROR, "页面大小必须在1-50之间");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        // 验证权限：只有应用创建者和管理员可以查看

        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        boolean isCreator = app.getUserId().equals(loginUser.getId());
        ThrowUtils.throwIf(!isAdmin && !isCreator, ErrorCode.NO_AUTH_ERROR, "无权查看该应用的对话历史");
        // 构建查询条件
        ChatHistoryQueryRequest queryRequest = new ChatHistoryQueryRequest();
        queryRequest.setAppId(appId);
        queryRequest.setLastCreateTime(lastCreateTime);
        QueryWrapper queryWrapper = this.getQueryWrapper(queryRequest);
        // 查询数据
        return this.page(Page.of(1, pageSize), queryWrapper);
    }

    @Override
    public int loadChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory, int maxCount) {
        try {
            // 直接构造查询条件，起始点为 1 而不是 0，用于排除最新的用户消息
            QueryWrapper queryWrapper = QueryWrapper.create()
                    .eq(ChatHistory::getAppId, appId)
                    .orderBy(ChatHistory::getCreateTime, false)
                    .limit(1, maxCount);
            List<ChatHistory> historyList = this.list(queryWrapper);
            if (CollUtil.isEmpty(historyList)) {
                return 0;
            }
            // 反转列表，确保按时间正序（老的在前，新的在后）

            // 按时间顺序添加到记忆中
            int loadedCount = 0;
            // 先清理历史缓存，防止重复加载
            chatMemory.clear();
            for (ChatHistory history : historyList) {
                if (MessageTypeEnum.USER.getValue().equals(history.getMessageType())) {
                    chatMemory.add(UserMessage.from(history.getMessage()));
                    loadedCount++;
                } else if (MessageTypeEnum.AI.getValue().equals(history.getMessageType())) {
                    chatMemory.add(AiMessage.from(history.getMessage()));
                    loadedCount++;
                }
            }
//            log.info("成功为 appId: {} 加载了 {} 条历史对话", appId, loadedCount);
            return loadedCount;
        } catch (Exception e) {
//            log.error("加载历史对话失败，appId: {}, error: {}", appId, e.getMessage(), e);
            // 加载失败不影响系统运行，只是没有历史上下文
            return 0;
        }
    }

}