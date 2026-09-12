package com.ai.service;

import com.ai.model.entity.TtsVoiceProfile;

import java.util.Map;

public interface TtsProxyService {

    boolean isAvailable();

    void preloadReferAudio(String refAudioPath);

    byte[] synthesize(String text, TtsVoiceProfile voice, Map<String, Object> extra);
}
