package com.ai.mapper.chat;

import com.ai.model.dto.chat.ChatMessageCountRow;
import com.ai.model.entity.ChatMessage;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    /**
     * 按会话批量取消息计数：对当前会话集合一次 GROUP BY 取回，替代逐会话 COUNT 的 N+1。
     * 自定义 SQL 不经过 MyBatis-Flex，逻辑删除需显式 is_delete = 0，
     * 与单会话 countByConversation（QueryWrapper 自动追加逻辑删除）行为对齐。
     * 别名显式落驼峰，不依赖 mapUnderscoreToCamelCase 配置；
     * conversationIds 为空时由调用方直接返回空 Map，不落库。
     */
    @Select("<script>SELECT conversation_id AS conversationId, COUNT(*) AS messageCount "
            + "FROM chat_message WHERE is_delete = 0 AND conversation_id IN "
            + "<foreach collection='conversationIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "GROUP BY conversation_id</script>")
    List<ChatMessageCountRow> selectMessageCountGrouped(@Param("conversationIds") List<Long> conversationIds);
}
