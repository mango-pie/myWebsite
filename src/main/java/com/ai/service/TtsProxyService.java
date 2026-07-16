package com.ai.service;

import com.ai.model.entity.TtsVoiceProfile;

import java.util.Map;

public interface TtsProxyService {

    boolean isAvailable();

    void preloadReferAudio(String refAudioPath);

    byte[] synthesize(String text, TtsVoiceProfile voice, Map<String, Object> extra);

    /** 超时等设置变更后失效 HTTP 客户端缓存。 */
    default void invalidateClient() {
    }
}
