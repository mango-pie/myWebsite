package com.ai.service;

import com.ai.model.dto.chat.ChatMessageSegment;
import com.ai.model.vo.chat.ChatAttachmentVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 聊天图片附件：BFF 本地 Vision 转述 + caption 缓存（不再上传 AstrBot）。
 */
public interface ChatAttachmentService {

    ChatAttachmentVO uploadImage(MultipartFile file);

    /**
     * 将含 image segment 的请求转为发给 AstrBot 的纯文本 message。
     */
    String buildPlainMessageForAstrBot(String message, List<ChatMessageSegment> segments);

    boolean hasImageSegments(List<ChatMessageSegment> segments);
}
