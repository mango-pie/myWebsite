package com.ai.service.chat.support;

import cn.hutool.core.util.StrUtil;
import com.ai.model.dto.chat.ChatMessageSegment;

import java.util.List;

/**
 * 角色聊天消息内容工具（落库展示文本提取等）。
 */
public final class ChatMessageUtils {

    private ChatMessageUtils() {
    }

    /**
     * 从 segments / message 提取写入 chat_message.content 的纯文本。
     * 图片不落库，仅文字或占位符。
     */
    public static String extractPersistContent(String message, List<ChatMessageSegment> segments) {
        if (segments != null && !segments.isEmpty()) {
            StringBuilder textBuilder = new StringBuilder();
            int imageCount = 0;
            for (ChatMessageSegment segment : segments) {
                if (segment == null || StrUtil.isBlank(segment.getType())) {
                    continue;
                }
                String type = segment.getType().trim().toLowerCase();
                if ("plain".equals(type) && StrUtil.isNotBlank(segment.getText())) {
                    if (textBuilder.length() > 0) {
                        textBuilder.append('\n');
                    }
                    textBuilder.append(segment.getText().trim());
                } else if ("image".equals(type)) {
                    imageCount++;
                }
            }
            if (textBuilder.length() > 0) {
                return textBuilder.toString();
            }
            if (imageCount > 0) {
                return imageCount == 1 ? "[图片]" : "[图片]×" + imageCount;
            }
        }
        return StrUtil.blankToDefault(message, "");
    }

    /**
     * 从 segments / message 提取用户输入的纯文本（不含图片占位）。
     */
    public static String extractUserText(String message, List<ChatMessageSegment> segments) {
        if (segments != null && !segments.isEmpty()) {
            StringBuilder textBuilder = new StringBuilder();
            for (ChatMessageSegment segment : segments) {
                if (segment == null || StrUtil.isBlank(segment.getType())) {
                    continue;
                }
                if ("plain".equalsIgnoreCase(segment.getType().trim())
                        && StrUtil.isNotBlank(segment.getText())) {
                    if (textBuilder.length() > 0) {
                        textBuilder.append('\n');
                    }
                    textBuilder.append(segment.getText().trim());
                }
            }
            if (textBuilder.length() > 0) {
                return textBuilder.toString();
            }
        }
        return StrUtil.blankToDefault(message, "");
    }

    /**
     * 将用户文字 + 图片描述拼成发给 AstrBot 的纯文本 message。
     */
    public static String buildAstrBotPlainMessage(String userText, List<String> captions) {
        if (captions == null || captions.isEmpty()) {
            return StrUtil.blankToDefault(userText, "");
        }
        StringBuilder builder = new StringBuilder();
        if (StrUtil.isNotBlank(userText)) {
            builder.append(userText.trim());
        }
        for (int i = 0; i < captions.size(); i++) {
            String caption = StrUtil.trim(captions.get(i));
            if (StrUtil.isBlank(caption)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            if (captions.size() == 1) {
                builder.append("[图片描述]：").append(caption);
            } else {
                builder.append("[图片").append(i + 1).append("]：").append(caption);
            }
        }
        return builder.toString();
    }
}
