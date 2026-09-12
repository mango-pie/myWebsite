package com.ai.service.knowledge.support;

import com.ai.config.ConditionalOnModule;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 精读任务完成通知：SSE 推送 job 状态变化（WAITING / SUCCESS / FAILED）。
 * 进程内注册表（单管理员、单实例适用）；前端重连时仍可轮询兜底。
 */
@Slf4j
@ConditionalOnModule("knowledge")
@Component
public class ReadingJobNotifier {

    private static final long SSE_TIMEOUT_MS = 300_000L;

    private final Map<Long, List<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    @Resource
    private ObjectMapper objectMapper;

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        List<SseEmitter> list = emittersByUser.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(e -> remove(userId, emitter));
        return emitter;
    }

    /** 推送 {jobId, status, progress, success} 给指定用户的所有连接 */
    public void notify(Long userId, Long jobId, String status, String progress) {
        if (userId == null || jobId == null) {
            return;
        }
        List<SseEmitter> list = emittersByUser.get(userId);
        if (list == null || list.isEmpty()) {
            return;
        }
        try {
            String data = objectMapper.writeValueAsString(Map.of(
                    "jobId", jobId,
                    "status", status == null ? "" : status,
                    "progress", progress == null ? "" : progress,
                    "success", "SUCCESS".equals(status)));
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().name("job_update").data(data));
                } catch (IOException | IllegalStateException e) {
                    remove(userId, emitter);
                }
            }
        } catch (Exception e) {
            log.warn("Reading job notify failed, userId={}, jobId={}", userId, jobId, e);
        }
    }

    private void remove(Long userId, SseEmitter emitter) {
        List<SseEmitter> list = emittersByUser.get(userId);
        if (list != null) {
            list.remove(emitter);
            emitter.complete();
        }
    }
}
