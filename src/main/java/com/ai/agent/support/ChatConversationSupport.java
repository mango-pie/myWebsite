package com.ai.agent.support;

import cn.hutool.core.util.StrUtil;
import com.ai.model.entity.ChatConversation;
import com.ai.model.vo.chat.ChatConversationVO;
import com.ai.service.ChatConversationService;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerErrorException;

@Component
public class ChatConversationSupport {

    private final ChatConversationService chatConversationService;

    public ChatConversationSupport(ChatConversationService chatConversationService) {
        this.chatConversationService = chatConversationService;
    }

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
