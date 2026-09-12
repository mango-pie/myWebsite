package com.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.ai.config.ChatImageCaptionProperties;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.service.ChatImageCaptionService;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
public class ChatImageCaptionServiceImpl implements ChatImageCaptionService {

    @Resource
    private ChatImageCaptionProperties captionProperties;

    @Autowired(required = false)
    @Qualifier("imageCaptionChatModel")
    private ChatModel imageCaptionChatModel;

    @Override
    public String caption(byte[] imageBytes, String mimeType) {
        if (!captionProperties.isEnabled()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "图片转述功能未启用");
        }
        if (imageCaptionChatModel == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "图片转述模型未配置，请设置 ZHIPU_API_KEY");
        }
        if (StrUtil.isBlank(captionProperties.getApiKey())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "图片转述 API Key 未配置，请设置 ZHIPU_API_KEY");
        }
        if (imageBytes == null || imageBytes.length == 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片内容为空");
        }

        String effectiveMime = StrUtil.blankToDefault(mimeType, "image/jpeg");
        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        UserMessage userMessage = UserMessage.from(
                TextContent.from(captionProperties.getPrompt()),
                ImageContent.from(base64, effectiveMime)
        );

        try {
            CompletableFuture<ChatResponse> future = CompletableFuture.supplyAsync(
                    () -> imageCaptionChatModel.chat(userMessage)
            );
            ChatResponse response = future.get(
                    captionProperties.getTimeoutSeconds(),
                    TimeUnit.SECONDS
            );
            String text = response != null && response.aiMessage() != null
                    ? response.aiMessage().text()
                    : null;
            if (StrUtil.isBlank(text)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "图片描述结果为空");
            }
            return text.trim();
        } catch (TimeoutException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "图片描述超时，请稍后重试");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("图片描述失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "图片描述失败: " + e.getMessage());
        }
    }
}
