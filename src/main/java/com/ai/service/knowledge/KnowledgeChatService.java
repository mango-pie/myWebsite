package com.ai.service.knowledge;

import com.ai.model.dto.knowledge.KnowledgeChatRequest;
import com.ai.model.vo.knowledge.KnowledgeChatResponse;
import com.ai.model.vo.knowledge.KnowledgeConversationVO;
import com.ai.model.vo.knowledge.KnowledgeMessageVO;
import com.mybatisflex.core.paginate.Page;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface KnowledgeChatService {

    KnowledgeChatResponse chat(KnowledgeChatRequest request, Long userId);

    SseEmitter streamChat(KnowledgeChatRequest request, Long userId);

    List<KnowledgeConversationVO> listConversations(Long knowledgeBaseId, Long userId);

    Page<KnowledgeMessageVO> listMessages(Long conversationId, Long userId, int pageNum, int pageSize);

    boolean deleteConversation(Long conversationId, Long userId);
}
