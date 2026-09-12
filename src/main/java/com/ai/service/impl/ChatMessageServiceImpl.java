package com.ai.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.mapper.ChatMessageMapper;
import com.ai.model.entity.ChatMessage;
import com.ai.model.enums.ChatMessageSourceEnum;
import com.ai.model.enums.MessageTypeEnum;
import com.ai.model.vo.chat.ChatMessageVO;
import com.ai.service.ChatConversationService;
import com.ai.service.ChatMessageService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage>
        implements ChatMessageService {

    @Resource
    @Lazy
    private ChatConversationService chatConversationService;

    @Override
    public Long addMessage(Long conversationId, Long userId, String messageType, String content, String source) {
        if (conversationId == null || conversationId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话ID无效");
        }
        if (StrUtil.isBlank(messageType)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "消息类型不能为空");
        }
        if (StrUtil.isBlank(content)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "消息内容不能为空");
        }
        MessageTypeEnum messageTypeEnum = MessageTypeEnum.getByValue(messageType);
        if (messageTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的消息类型: " + messageType);
        }

        chatConversationService.getOwnedConversation(conversationId, userId);

        String effectiveSource = ChatMessageSourceEnum.getByValue(source).getValue();
        LocalDateTime now = LocalDateTime.now();
        ChatMessage message = ChatMessage.builder()
                .conversationId(conversationId)
                .userId(userId)
                .messageType(messageType)
                .content(content)
                .source(effectiveSource)
                .createTime(now)
                .updateTime(now)
                .isDelete(0)
                .build();
        if (!this.save(message)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "保存消息失败");
        }
        return message.getId();
    }

    @Override
    public Page<ChatMessageVO> listByConversation(Long conversationId, Long userId, int pageNum, int pageSize) {
        if (pageNum <= 0) {
            pageNum = 1;
        }
        if (pageSize <= 0 || pageSize > 100) {
            pageSize = 20;
        }
        chatConversationService.getOwnedConversation(conversationId, userId);

        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq(ChatMessage::getConversationId, conversationId)
                .orderBy(ChatMessage::getCreateTime, true);

        Page<ChatMessage> page = this.page(Page.of(pageNum, pageSize), queryWrapper);
        Page<ChatMessageVO> voPage = new Page<>();
        voPage.setPageNumber(page.getPageNumber());
        voPage.setPageSize(page.getPageSize());
        voPage.setTotalRow(page.getTotalRow());
        voPage.setTotalPage(page.getTotalPage());
        voPage.setRecords(page.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public List<ChatMessageVO> listLatest(Long conversationId, Long userId, int limit) {
        if (limit <= 0 || limit > 100) {
            limit = 20;
        }
        chatConversationService.getOwnedConversation(conversationId, userId);

        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq(ChatMessage::getConversationId, conversationId)
                .orderBy(ChatMessage::getCreateTime, false)
                .limit(limit);
        List<ChatMessage> messages = this.list(queryWrapper);
        List<ChatMessageVO> result = messages.stream().map(this::toVO).collect(Collectors.toList());
        java.util.Collections.reverse(result);
        return result;
    }

    @Override
    public long countByConversation(Long conversationId) {
        if (conversationId == null || conversationId <= 0) {
            return 0;
        }
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq(ChatMessage::getConversationId, conversationId);
        return this.count(queryWrapper);
    }

    private ChatMessageVO toVO(ChatMessage message) {
        ChatMessageVO vo = new ChatMessageVO();
        BeanUtil.copyProperties(message, vo);
        return vo;
    }
}
