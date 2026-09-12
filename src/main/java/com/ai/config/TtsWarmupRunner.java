package com.ai.config;

import com.ai.model.entity.TtsVoiceProfile;
import com.ai.service.TtsProxyService;
import com.ai.service.TtsVoiceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

@Slf4j
@Component
public class TtsWarmupRunner implements ApplicationRunner {

    @Resource
    private TtsVoiceService ttsVoiceService;

    @Resource
    private TtsProxyService ttsProxyService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            ttsVoiceService.ensureSeedVoice();
            if (!ttsProxyService.isAvailable()) {
                log.warn("GPT-SoVITS not available at startup, skip TTS refer-audio warmup");
                return;
            }
            TtsVoiceProfile def = ttsVoiceService.getDefaultVoice();
            ttsVoiceService.initReferAudio(def.getId());
            log.info("TTS refer-audio preloaded for voice: {}", def.getName());
        } catch (Exception e) {
            log.warn("TTS warmup skipped: {}", e.getMessage());
        }
    }
}
