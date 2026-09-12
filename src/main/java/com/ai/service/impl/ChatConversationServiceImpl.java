package com.ai.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.mapper.ChatConversationMapper;
import com.ai.model.entity.ChatConversation;
import com.ai.model.vo.chat.ChatConfigVO;
import com.ai.model.vo.chat.ChatConversationVO;
import com.ai.service.AstrBotChatService;
import com.ai.service.ChatConversationService;
import com.ai.service.ChatMessageService;
import com.ai.utils.ChatSessionUtils;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ChatConversationServiceImpl extends ServiceImpl<ChatConversationMapper, ChatConversation>
        implements ChatConversationService {

    @Resource
    private AstrBotChatService astrBotChatService;

    @Resource
    private ChatMessageService chatMessageService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatConversationVO resolveDefault(Long userId, String configId) {
        String effectiveConfigId = normalizeConfigId(configId);
        ChatConversation existing = findDefaultConversation(userId, effectiveConfigId);
        if (existing != null) {
            return toVO(existing, true);
        }
        return toVO(insertConversation(userId, effectiveConfigId, null, true), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatConversationVO createConversation(Long userId, String configId, String title) {
        String effectiveConfigId = normalizeConfigId(configId);
        ChatConversation conversation = insertConversation(userId, effectiveConfigId, title, false);
        return toVO(conversation, true);
    }

    @Override
    public List<ChatConversationVO> listConversations(Long userId, String configId) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq(ChatConversation::getUserId, userId)
                .orderBy(ChatConversation::getLastMessageAt, false)
                .orderBy(ChatConversation::getCreateTime, false);
        if (StrUtil.isNotBlank(configId)) {
            queryWrapper.eq(ChatConversation::getConfigId, configId.trim());
        }
        return this.list(queryWrapper).stream()
                .map(conv -> toVO(conv, true))
                .collect(Collectors.toList());
    }

    @Override
    public ChatConversationVO getConversationVO(Long conversationId, Long userId) {
        ChatConversation conversation = getOwnedConversation(conversationId, userId);
        return toVO(conversation, true);
    }

    @Override
    public ChatConversation getOwnedConversation(Long conversationId, Long userId) {
        if (conversationId == null || conversationId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话ID无效");
        }
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户ID无效");
        }
        ChatConversation conversation = this.getById(conversationId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "会话不存在");
        }
        if (!userId.equals(conversation.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权访问该会话");
        }
        return conversation;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteConversation(Long conversationId, Long userId) {
        getOwnedConversation(conversationId, userId);
        return this.removeById(conversationId);
    }

    @Override
    public void touchLastMessageAt(Long conversationId) {
        ChatConversation update = new ChatConversation();
        update.setId(conversationId);
        update.setLastMessageAt(LocalDateTime.now());
        update.setUpdateTime(LocalDateTime.now());
        this.updateById(update);
    }

    private ChatConversation findDefaultConversation(Long userId, String configId) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq(ChatConversation::getUserId, userId)
                .eq(ChatConversation::getConfigId, configId)
                .eq(ChatConversation::getIsDefault, 1)
                .limit(1);
        return this.getOne(queryWrapper);
    }

    private ChatConversation insertConversation(Long userId, String configId, String title, boolean isDefault) {
        if (isDefault) {
            ChatConversation again = findDefaultConversation(userId, configId);
            if (again != null) {
                return again;
            }
        }

        String configName = resolveConfigName(configId);
        String effectiveTitle = StrUtil.blankToDefault(title,
                "与 " + StrUtil.blankToDefault(configName, configId) + " 的对话");

        LocalDateTime now = LocalDateTime.now();
        ChatConversation conversation = ChatConversation.builder()
                .userId(userId)
                .configId(configId)
                .configName(configName)
                .title(effectiveTitle)
                .astrbotSessionId("pending_" + UUID.randomUUID())
                .isDefault(isDefault ? 1 : 0)
                .createTime(now)
                .updateTime(now)
                .isDelete(0)
                .build();

        if (!this.save(conversation)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "创建会话失败");
        }

        String sessionId = ChatSessionUtils.buildAstrbotSessionId(userId, configId, conversation.getId());
        ChatConversation sessionUpdate = new ChatConversation();
        sessionUpdate.setId(conversation.getId());
        sessionUpdate.setAstrbotSessionId(sessionId);
        sessionUpdate.setUpdateTime(LocalDateTime.now());
        if (!this.updateById(sessionUpdate)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "初始化会话失败");
        }
        conversation.setAstrbotSessionId(sessionId);
        return conversation;
    }

    private String resolveConfigName(String configId) {
        try {
            List<ChatConfigVO> configs = astrBotChatService.listConfigs();
            for (ChatConfigVO config : configs) {
                if (configId.equals(config.getId())) {
                    return StrUtil.blankToDefault(config.getName(), configId);
                }
            }
        } catch (Exception ignored) {
            // AstrBot 不可达时使用 configId 作为展示名
        }
        return configId;
    }

    private ChatConversationVO toVO(ChatConversation conversation, boolean withCount) {
        ChatConversationVO vo = new ChatConversationVO();
        BeanUtil.copyProperties(conversation, vo);
        vo.setIsDefault(conversation.getIsDefault() != null && conversation.getIsDefault() == 1);
        if (withCount) {
            vo.setMessageCount(chatMessageService.countByConversation(conversation.getId()));
        }
        return vo;
    }

    private String normalizeConfigId(String configId) {
        if (StrUtil.isBlank(configId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "configId 不能为空");
        }
        return configId.trim();
    }
}
