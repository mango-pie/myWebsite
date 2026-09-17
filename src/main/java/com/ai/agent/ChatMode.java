package com.ai.agent;

import cn.hutool.core.util.StrUtil;

/**
 * 聊天模式：ASK 普通问答（默认，直接走大模型），AGENT 工具调用模式（可操作博客/日记/待办等）。
 * 传入非法值时一律回退到 ASK。
 */
public enum ChatMode {

    ASK("ask"),
    AGENT("agent");

    private final String value;

    ChatMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ChatMode from(String raw) {
        if (StrUtil.isBlank(raw)) {
            return ASK;
        }
        for (ChatMode mode : values()) {
            if (mode.value.equalsIgnoreCase(raw.trim())) {
                return mode;
            }
        }
        return ASK;
    }
}
