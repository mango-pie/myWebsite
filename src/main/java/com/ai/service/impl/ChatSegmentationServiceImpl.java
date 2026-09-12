package com.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.ai.config.ChatSegmentationProperties;
import com.ai.service.ChatSegmentationService;
import com.ai.utils.ChatSegmentationUtils;
import dev.langchain4j.model.chat.ChatModel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
public class ChatSegmentationServiceImpl implements ChatSegmentationService {

    @Resource
    private ChatSegmentationProperties segmentationProperties;

    @Autowired(required = false)
    @Qualifier("openAiChatModel")
    private ChatModel chatModel;

    @Override
    public List<String> segment(String text) {
        if (StrUtil.isBlank(text)) {
            return List.of();
        }
        String trimmed = text.trim();
        if (!segmentationProperties.isEnabled() || trimmed.length() < segmentationProperties.getMinLength()) {
            log.debug("跳过分段: enabled={}, length={}", segmentationProperties.isEnabled(), trimmed.length());
            return List.of(trimmed);
        }

        List<String> llmSegments = tryLlmSegment(trimmed);
        if (ChatSegmentationUtils.hasMultipleSegments(llmSegments)) {
            log.info("智能分段 LLM 成功，共 {} 段，原文长度 {}", llmSegments.size(), trimmed.length());
            return llmSegments;
        }

        if (segmentationProperties.isFallbackToRules()) {
            List<String> ruleSegments = ChatSegmentationUtils.splitByPunctuationFallback(
                    trimmed, segmentationProperties.getMaxSegments());
            if (ChatSegmentationUtils.hasMultipleSegments(ruleSegments)) {
                log.info("智能分段 LLM 未拆条，已用标点规则兜底，共 {} 段，原文长度 {}",
                        ruleSegments.size(), trimmed.length());
                return ruleSegments;
            }
        }

        log.info("智能分段未产生多段结果，保持整段，原文长度 {}", trimmed.length());
        return List.of(trimmed);
    }

    private List<String> tryLlmSegment(String trimmed) {
        if (chatModel == null) {
            log.warn("ChatModel 未配置，跳过 LLM 分段（请检查 langchain4j.open-ai.chat-model / DASHSCOPE_API_KEY）");
            return List.of(trimmed);
        }
        try {
            String prompt = ChatSegmentationUtils.buildSegmentationPrompt(
                    trimmed,
                    segmentationProperties.getStyle(),
                    segmentationProperties.getMaxSegments()
            );
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> chatModel.chat(prompt));
            String raw = future.get(
                    (long) (segmentationProperties.getTimeoutSeconds() * 1000),
                    TimeUnit.MILLISECONDS
            );
            List<String> segments = ChatSegmentationUtils.parseSegmentsFromModelOutput(
                    raw,
                    trimmed,
                    segmentationProperties.getMaxSegments()
            );
            if (segments.size() <= 1) {
                log.debug("LLM 分段结果为单段，raw 长度={}", raw == null ? 0 : raw.length());
                return List.of(trimmed);
            }
            return segments;
        } catch (TimeoutException e) {
            log.warn("智能分段 LLM 调用超时（> {}s），尝试规则兜底", segmentationProperties.getTimeoutSeconds());
            return List.of(trimmed);
        } catch (Exception e) {
            log.warn("智能分段 LLM 调用失败（{}），尝试规则兜底: {}",
                    chatModel.getClass().getSimpleName(), e.getMessage());
            return List.of(trimmed);
        }
    }
}
