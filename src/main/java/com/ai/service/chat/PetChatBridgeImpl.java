package com.ai.service.chat;

import com.ai.agent.ChatOrchestrator;
import com.ai.config.ConditionalOnModule;
import com.ai.model.dto.chat.ChatRequest;
import com.ai.model.vo.chat.ChatConfigVO;
import com.ai.model.vo.chat.ChatConversationVO;
import com.ai.model.vo.chat.ChatMessageVO;
import com.ai.model.vo.chat.ChatStreamEvent;
import com.ai.service.AstrBotChatService;
import com.ai.service.ChatConversationService;
import com.ai.service.ChatMessageService;
import com.ai.service.pet.spi.PetChatBridge;
import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * chat 模块提供的 {@link PetChatBridge} 真实实现，仅在 chat 模块启用时注册。
 */
@ConditionalOnModule("chat")
@Component
public class PetChatBridgeImpl implements PetChatBridge {

    @Resource
    private AstrBotChatService astrBotChatService;

    @Resource
    private ChatConversationService chatConversationService;

    @Resource
    private ChatMessageService chatMessageService;

    @Resource
    private ChatOrchestrator chatOrchestrator;

    @Override
    public List<ChatConfigVO> listConfigs() {
        return astrBotChatService.listConfigs();
    }

    @Override
    public ChatConversationVO resolveDefault(Long userId, String configId) {
        return chatConversationService.resolveDefault(userId, configId);
    }

    @Override
    public Page<ChatMessageVO> listMessages(Long conversationId, Long userId, int pageNum, int pageSize) {
        return chatMessageService.listByConversation(conversationId, userId, pageNum, pageSize);
    }

    @Override
    public Flux<ChatStreamEvent> chat(ChatRequest request, HttpServletRequest httpRequest) {
        return chatOrchestrator.chat(request, httpRequest);
    }
}
