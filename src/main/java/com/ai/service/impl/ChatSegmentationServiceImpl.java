package com.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.ai.constant.AiUsageSceneConstant;
import com.ai.model.dto.ops.AiUsageRecord;
import com.ai.service.AiUsageLogService;
import com.ai.service.ChatSegmentationService;
import com.ai.setting.runtime.ChatRuntimeSettings;
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
    private ChatRuntimeSettings chatRuntimeSettings;

    @Autowired(required = false)
    @Qualifier("openAiChatModel")
    private ChatModel chatModel;

    @Resource
    private AiUsageLogService aiUsageLogService;

    @Override
    public List<String> segment(String text) {
        if (StrUtil.isBlank(text)) {
            return List.of();
        }
        String trimmed = text.trim();
        if (!chatRuntimeSettings.segmentationEnabled()
                || trimmed.length() < chatRuntimeSettings.segmentationMinLength()) {
            log.debug("跳过分段: enabled={}, length={}",
                    chatRuntimeSettings.segmentationEnabled(), trimmed.length());
            return List.of(trimmed);
        }

        List<String> llmSegments = tryLlmSegment(trimmed);
        if (ChatSegmentationUtils.hasMultipleSegments(llmSegments)) {
            log.info("智能分段 LLM 成功，共 {} 段，原文长度 {}", llmSegments.size(), trimmed.length());
            return llmSegments;
        }

        if (chatRuntimeSettings.segmentationFallbackToRules()) {
            List<String> ruleSegments = ChatSegmentationUtils.splitByPunctuationFallback(
                    trimmed, chatRuntimeSettings.segmentationMaxSegments());
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
        long startNs = System.nanoTime();
        try {
            String prompt = ChatSegmentationUtils.buildSegmentationPrompt(
                    trimmed,
                    chatRuntimeSettings.segmentationStyle(),
                    chatRuntimeSettings.segmentationMaxSegments()
            );
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> chatModel.chat(prompt));
            String raw = future.get(
                    (long) (chatRuntimeSettings.segmentationTimeoutSeconds() * 1000),
                    TimeUnit.MILLISECONDS
            );
            recordSegmentationUsage(true, startNs, null);
            List<String> segments = ChatSegmentationUtils.parseSegmentsFromModelOutput(
                    raw,
                    trimmed,
                    chatRuntimeSettings.segmentationMaxSegments()
            );
            if (segments.size() <= 1) {
                log.debug("LLM 分段结果为单段，raw 长度={}", raw == null ? 0 : raw.length());
                return List.of(trimmed);
            }
            return segments;
        } catch (TimeoutException e) {
            log.warn("智能分段 LLM 调用超时（> {}s），尝试规则兜底",
                    chatRuntimeSettings.segmentationTimeoutSeconds());
            recordSegmentationUsage(false, startNs, "timeout");
            return List.of(trimmed);
        } catch (Exception e) {
            log.warn("智能分段 LLM 调用失败（{}），尝试规则兜底: {}",
                    chatModel.getClass().getSimpleName(), e.getMessage());
            recordSegmentationUsage(false, startNs, e.getMessage());
            return List.of(trimmed);
        }
    }

    private void recordSegmentationUsage(boolean success, long startNs, String errorMessage) {
        long elapsedMs = Math.max(0L, (System.nanoTime() - startNs) / 1_000_000L);
        aiUsageLogService.record(AiUsageRecord.builder()
                .scene(AiUsageSceneConstant.SEGMENTATION)
                .modelName("segmentation")
                .responseTimeMs(elapsedMs)
                .status(success ? AiUsageSceneConstant.STATUS_SUCCESS : AiUsageSceneConstant.STATUS_ERROR)
                .errorMessage(errorMessage)
                .requestSummary("llm segmentation")
                .build());
    }
}
