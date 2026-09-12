package com.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.ai.config.ChatAttachmentProperties;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.model.dto.chat.ChatMessageSegment;
import com.ai.model.vo.chat.ChatAttachmentVO;
import com.ai.service.ChatAttachmentService;
import com.ai.service.ChatImageCaptionService;
import com.ai.utils.ChatMessageUtils;
import com.ai.utils.ImageUploadUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class ChatAttachmentServiceImpl implements ChatAttachmentService {

    private static final long MAX_ATTACHMENT_BYTES = 5L * 1024 * 1024;

    @Resource
    private ChatImageCaptionService chatImageCaptionService;

    @Resource
    private ChatAttachmentProperties chatAttachmentProperties;

    private Cache<String, String> captionCache;

    @PostConstruct
    void initCache() {
        int ttlMinutes = Math.max(1, chatAttachmentProperties.getAttachmentCacheTtlMinutes());
        captionCache = Caffeine.newBuilder()
                .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
                .maximumSize(10_000)
                .build();
    }

    @Override
    public ChatAttachmentVO uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "上传文件不能为空");
        }
        if (file.getSize() > MAX_ATTACHMENT_BYTES) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片大小不能超过 5MB");
        }
        if (!ImageUploadUtil.isImageFile(file)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "只允许上传 jpg、jpeg、png、gif、bmp 格式的图片");
        }

        String originalFilename = StrUtil.blankToDefault(file.getOriginalFilename(), "image.jpg");
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "读取上传文件失败: " + e.getMessage());
        }

        String mimeType = resolveMimeType(originalFilename, file.getContentType());
        String caption = chatImageCaptionService.caption(bytes, mimeType);
        String attachmentId = UUID.randomUUID().toString();
        captionCache.put(attachmentId, caption);

        ChatAttachmentVO vo = new ChatAttachmentVO();
        vo.setAttachmentId(attachmentId);
        vo.setType("image");
        vo.setFilename(originalFilename);
        return vo;
    }

    @Override
    public String buildPlainMessageForAstrBot(String message, List<ChatMessageSegment> segments) {
        if (!hasImageSegments(segments)) {
            return StrUtil.blankToDefault(message, "");
        }

        String userText = ChatMessageUtils.extractUserText(message, segments);
        List<String> captions = new ArrayList<>();
        for (ChatMessageSegment segment : segments) {
            if (segment == null || StrUtil.isBlank(segment.getType())) {
                continue;
            }
            if (!"image".equalsIgnoreCase(segment.getType().trim())) {
                continue;
            }
            String attachmentId = StrUtil.trim(segment.getAttachmentId());
            if (StrUtil.isBlank(attachmentId)) {
                continue;
            }
            String caption = captionCache.getIfPresent(attachmentId);
            if (StrUtil.isBlank(caption)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "图片已过期，请重新上传");
            }
            captions.add(caption);
        }
        if (captions.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "消息不能为空");
        }
        return ChatMessageUtils.buildAstrBotPlainMessage(userText, captions);
    }

    @Override
    public boolean hasImageSegments(List<ChatMessageSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return false;
        }
        for (ChatMessageSegment segment : segments) {
            if (segment != null
                    && "image".equalsIgnoreCase(StrUtil.trim(segment.getType()))
                    && StrUtil.isNotBlank(segment.getAttachmentId())) {
                return true;
            }
        }
        return false;
    }

    private String resolveMimeType(String filename, String contentType) {
        if (StrUtil.isNotBlank(contentType) && contentType.startsWith("image/")) {
            return contentType;
        }
        String extension = ImageUploadUtil.getFileExtension(filename).toLowerCase();
        return switch (extension) {
            case ".png" -> MediaType.IMAGE_PNG_VALUE;
            case ".gif" -> MediaType.IMAGE_GIF_VALUE;
            case ".bmp" -> "image/bmp";
            case ".webp" -> "image/webp";
            default -> MediaType.IMAGE_JPEG_VALUE;
        };
    }
}
