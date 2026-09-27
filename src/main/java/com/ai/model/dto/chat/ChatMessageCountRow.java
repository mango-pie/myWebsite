package com.ai.model.dto.chat;

import lombok.Data;

/**
 * chat_message SQL 聚合投影行：按 conversation_id 分组的消息计数，
 * 供会话列表一次查询批量装配 messageCount，替代逐会话 COUNT 的 N+1。
 */
@Data
public class ChatMessageCountRow {

    private Long conversationId;

    private Long messageCount;
}
