package com.ai.setting.runtime;

import com.ai.config.ConditionalOnModule;

import com.ai.config.GptSovitsProperties;
import com.ai.constant.SiteSettingConstant;
import com.ai.service.SiteSettingService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * tts 模块运行时读取：DB 覆盖 YAML Properties。
 */
@ConditionalOnModule("tts")
@Component
public class TtsRuntimeSettings {

    @Resource
    private SiteSettingService siteSettingService;
    @Resource
    private GptSovitsProperties gptSovitsProperties;

    public boolean enabled() {
        return siteSettingService.getBool(SiteSettingConstant.MODULE_TTS, "enabled", true);
    }

    public String defaultTextLang() {
        String yaml = gptSovitsProperties.getSeedVoice() != null
                ? gptSovitsProperties.getSeedVoice().getTextLang() : "zh";
        return siteSettingService.getString(SiteSettingConstant.MODULE_TTS, "default_text_lang", yaml);
    }

    public String defaultPromptLang() {
        String yaml = gptSovitsProperties.getSeedVoice() != null
                ? gptSovitsProperties.getSeedVoice().getPromptLang() : "zh";
        return siteSettingService.getString(SiteSettingConstant.MODULE_TTS, "default_prompt_lang", yaml);
    }

    public boolean seedVoiceEnabled() {
        boolean yaml = gptSovitsProperties.getSeedVoice() != null && gptSovitsProperties.getSeedVoice().isEnabled();
        return siteSettingService.getBool(SiteSettingConstant.MODULE_TTS, "seed_voice.enabled", yaml);
    }

    public String seedVoiceName() {
        String yaml = gptSovitsProperties.getSeedVoice() != null
                ? gptSovitsProperties.getSeedVoice().getName() : "达妮娅";
        return siteSettingService.getString(SiteSettingConstant.MODULE_TTS, "seed_voice.name", yaml);
    }

    public int connectTimeoutMs() {
        return siteSettingService.getInt(SiteSettingConstant.MODULE_TTS, "connect_timeout_ms",
                gptSovitsProperties.getConnectTimeoutMs());
    }

    public int readTimeoutMs() {
        return siteSettingService.getInt(SiteSettingConstant.MODULE_TTS, "read_timeout_ms",
                gptSovitsProperties.getReadTimeoutMs());
    }

    public int refAudioMaxSizeMb() {
        int yaml = gptSovitsProperties.getRefAudio() != null
                ? gptSovitsProperties.getRefAudio().getMaxSizeMb() : 10;
        return siteSettingService.getInt(SiteSettingConstant.MODULE_TTS, "ref_audio.max_size_mb", yaml);
    }
}
