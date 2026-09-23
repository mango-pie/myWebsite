package com.ai.service.chat.support;

import cn.hutool.core.util.StrUtil;

/**
 * 角色聊天 AstrBot session_id 构建工具。
 */
public final class ChatSessionUtils {

    private ChatSessionUtils() {
    }

    public static String buildAstrbotSessionId(Long userId, String configId, Long conversationId) {
        String effectiveConfigId = StrUtil.blankToDefault(configId, "default");
        return "chat_user_" + userId + "_cfg_" + effectiveConfigId + "_conv_" + conversationId;
    }
}
