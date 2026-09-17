package com.ai.agent.support;

import com.ai.config.ConditionalOnModule;

import cn.hutool.core.util.StrUtil;
import com.ai.model.entity.ChatConversation;
import com.ai.model.vo.chat.ChatConversationVO;
import com.ai.service.ChatConversationService;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerErrorException;

/**
 * 聊天会话定位辅助：按「conversationId 优先、configId 兜底」的规则解析会话，
 * ask 与 agent 两条链路共用，保证归属校验（getOwnedConversation）逻辑一致。
 */
@ConditionalOnModule("chat")
@Component
public class ChatConversationSupport {

    private final ChatConversationService chatConversationService;

    public ChatConversationSupport(ChatConversationService chatConversationService) {
        this.chatConversationService = chatConversationService;
    }

    /**
     * 传了 conversationId 则直接取所属会话；否则按 configId 找（必要时创建）默认会话；
     * 两者都缺时抛 500。
     */
    public ChatConversation resolveConversation(Long userId, Long conversationId, String configId) {
        if (conversationId != null && conversationId > 0) {
            return chatConversationService.getOwnedConversation(conversationId, userId);
        }
        if (StrUtil.isNotBlank(configId)) {
            ChatConversationVO vo = chatConversationService.resolveDefault(userId, configId);
            return chatConversationService.getOwnedConversation(vo.getId(), userId);
        }
        throw new ServerErrorException("conversationId 与 configId 至少传一个", null);
    }
}
