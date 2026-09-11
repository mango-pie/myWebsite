package com.ai.service.pet.spi;

import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.model.dto.chat.ChatRequest;
import com.ai.model.vo.chat.ChatConfigVO;
import com.ai.model.vo.chat.ChatConversationVO;
import com.ai.model.vo.chat.ChatMessageVO;
import com.ai.model.vo.chat.ChatStreamEvent;
import com.mybatisflex.core.paginate.Page;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;

/**
 * chat 模块关闭时的兜底实现；与 PetChatBridgeImpl 互斥（chat 开时只注册后者）。
 */
@Component
@ConditionalOnProperty(name = "app.modules.chat", havingValue = "false")
public class NoOpPetChatBridge implements PetChatBridge {

    @Override
    public List<ChatConfigVO> listConfigs() {
        return Collections.emptyList();
    }

    @Override
    public ChatConversationVO resolveDefault(Long userId, String configId) {
        throw disabled();
    }

    @Override
    public Page<ChatMessageVO> listMessages(Long conversationId, Long userId, int pageNum, int pageSize) {
        return new Page<>(pageNum, pageSize, 0);
    }

    @Override
    public Flux<ChatStreamEvent> chat(ChatRequest request, HttpServletRequest httpRequest) {
        throw disabled();
    }

    private BusinessException disabled() {
        return new BusinessException(ErrorCode.OPERATION_ERROR, "chat 模块未启用");
    }
}
