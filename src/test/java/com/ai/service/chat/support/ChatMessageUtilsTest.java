package com.ai.service.chat.support;

import com.ai.model.dto.chat.ChatMessageSegment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatMessageUtilsTest {

    @Test
    void extractPersistContent_plainTextOnly() {
        assertEquals("你好", ChatMessageUtils.extractPersistContent("你好", null));
    }

    @Test
    void extractPersistContent_textAndImage() {
        List<ChatMessageSegment> segments = List.of(
                segment("plain", "分析这张图", null),
                segment("image", null, "abc-123")
        );
        assertEquals("分析这张图", ChatMessageUtils.extractPersistContent("", segments));
    }

    @Test
    void extractPersistContent_imageOnly() {
        List<ChatMessageSegment> segments = List.of(
                segment("image", null, "abc-123")
        );
        assertEquals("[图片]", ChatMessageUtils.extractPersistContent("", segments));
    }

    @Test
    void extractPersistContent_multipleImagesOnly() {
        List<ChatMessageSegment> segments = List.of(
                segment("image", null, "a"),
                segment("image", null, "b")
        );
        assertEquals("[图片]×2", ChatMessageUtils.extractPersistContent("", segments));
    }

    @Test
    void buildAstrBotPlainMessage_textAndSingleCaption() {
        String result = ChatMessageUtils.buildAstrBotPlainMessage("看看", List.of("一只橘猫"));
        assertEquals("看看\n\n[图片描述]：一只橘猫", result);
    }

    @Test
    void buildAstrBotPlainMessage_multipleCaptions() {
        String result = ChatMessageUtils.buildAstrBotPlainMessage("", List.of("猫", "狗"));
        assertEquals("[图片1]：猫\n\n[图片2]：狗", result);
    }

    @Test
    void extractUserText_fromSegments() {
        List<ChatMessageSegment> segments = List.of(
                segment("plain", "分析", null),
                segment("image", null, "id-1")
        );
        assertEquals("分析", ChatMessageUtils.extractUserText("", segments));
    }

    private static ChatMessageSegment segment(String type, String text, String attachmentId) {
        ChatMessageSegment segment = new ChatMessageSegment();
        segment.setType(type);
        segment.setText(text);
        segment.setAttachmentId(attachmentId);
        return segment;
    }
}
