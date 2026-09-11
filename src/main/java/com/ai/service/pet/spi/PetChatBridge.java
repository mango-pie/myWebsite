package com.ai.service.pet.spi;

import com.ai.model.dto.chat.ChatRequest;
import com.ai.model.vo.chat.ChatConfigVO;
import com.ai.model.vo.chat.ChatConversationVO;
import com.ai.model.vo.chat.ChatMessageVO;
import com.ai.model.vo.chat.ChatStreamEvent;
import com.mybatisflex.core.paginate.Page;
import jakarta.servlet.http.HttpServletRequest;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Pet 聊天能力 SPI。由 pet 平台侧定义，chat 模块提供真实实现，
 * chat 关闭时由 {@link NoOpPetChatBridge} 兜底，避免 {@code PetFacade} 强依赖 chat 的 Service Bean。
 */
public interface PetChatBridge {

    List<ChatConfigVO> listConfigs();

    ChatConversationVO resolveDefault(Long userId, String configId);

    Page<ChatMessageVO> listMessages(Long conversationId, Long userId, int pageNum, int pageSize);

    Flux<ChatStreamEvent> chat(ChatRequest request, HttpServletRequest httpRequest);
}
