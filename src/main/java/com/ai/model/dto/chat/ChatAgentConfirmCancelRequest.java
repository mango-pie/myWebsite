package com.ai.model.dto.chat;

import lombok.Data;

import java.io.Serializable;

/**
 * Agent L2 确认取消请求体：用户点击确认卡片的「取消」时提交 confirmToken。
 */
@Data
public class ChatAgentConfirmCancelRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * confirm_required 事件下发的待确认令牌。
     */
    private String confirmToken;
}
