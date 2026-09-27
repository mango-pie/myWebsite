package com.ai.service;

import com.ai.model.entity.ChatMessage;
import com.ai.model.vo.chat.ChatMessageVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.service.IService;

import java.util.List;
import java.util.Map;

public interface ChatMessageService extends IService<ChatMessage> {

    Long addMessage(Long conversationId, Long userId, String messageType, String content, String source);

    Page<ChatMessageVO> listByConversation(Long conversationId, Long userId, int pageNum, int pageSize);

    List<ChatMessageVO> listLatest(Long conversationId, Long userId, int limit);

    long countByConversation(Long conversationId);

    /**
     * 批量取会话消息计数：一次 GROUP BY 取回，key 为会话 id；
     * 无消息的会话不在返回 Map 中，调用方按 0 处理。
     */
    Map<Long, Long> countByConversations(List<Long> conversationIds);
}
