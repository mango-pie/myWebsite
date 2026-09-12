package com.ai.service.impl;

import com.ai.config.ChatAttachmentProperties;
import com.ai.config.ChatImageCaptionProperties;
import com.ai.exception.BusinessException;
import com.ai.model.dto.chat.ChatMessageSegment;
import com.ai.service.ChatImageCaptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatAttachmentServiceImplTest {

    private ChatAttachmentServiceImpl service;
    private ChatImageCaptionService captionService;

    @BeforeEach
    void setUp() {
        service = new ChatAttachmentServiceImpl();
        captionService = mock(ChatImageCaptionService.class);
        ChatAttachmentProperties attachmentProperties = new ChatAttachmentProperties();
        attachmentProperties.setAttachmentCacheTtlMinutes(120);
        ReflectionTestUtils.setField(service, "chatImageCaptionService", captionService);
        ReflectionTestUtils.setField(service, "chatAttachmentProperties", attachmentProperties);
        service.initCache();
    }

    @Test
    void uploadImage_cachesCaption() {
        when(captionService.caption(any(), anyString())).thenReturn("橘猫在窗台");
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );

        var vo = service.uploadImage(file);
        String plain = service.buildPlainMessageForAstrBot("看看", List.of(
                segment("plain", "看看", null),
                segment("image", null, vo.getAttachmentId())
        ));

        assertEquals("看看\n\n[图片描述]：橘猫在窗台", plain);
    }

    @Test
    void buildPlainMessageForAstrBot_cacheMiss() {
        ChatMessageSegment imageSegment = segment("image", null, "missing-id");
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.buildPlainMessageForAstrBot("看看", List.of(imageSegment))
        );
        assertTrue(ex.getMessage().contains("过期"));
    }

    private static ChatMessageSegment segment(String type, String text, String attachmentId) {
        ChatMessageSegment segment = new ChatMessageSegment();
        segment.setType(type);
        segment.setText(text);
        segment.setAttachmentId(attachmentId);
        return segment;
    }
}
