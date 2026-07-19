package com.ai.setting.module;

import com.ai.config.ConditionalOnModule;

import com.ai.config.GptSovitsProperties;
import com.ai.constant.SiteSettingConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.service.TtsProxyService;
import com.ai.setting.SettingFieldSchema;
import com.ai.setting.SettingModule;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ConditionalOnModule("tts")
@Component
public class TtsModule implements SettingModule {

    private static final Set<String> LANGS = Set.of("zh", "en", "ja", "ko", "auto");

    @Resource
    private GptSovitsProperties gptSovitsProperties;

    @Lazy
    @Resource
    private TtsProxyService ttsProxyService;

    @Override
    public String code() {
        return SiteSettingConstant.MODULE_TTS;
    }

    @Override
    public String displayName() {
        return "语音 TTS";
    }

    @Override
    public String phase() {
        return "P2";
    }

    @Override
    public List<SettingFieldSchema> schema() {
        GptSovitsProperties.SeedVoice seed = gptSovitsProperties.getSeedVoice();
        GptSovitsProperties.RefAudio ref = gptSovitsProperties.getRefAudio();
        return List.of(
                bool("enabled", "启用 TTS", true, true),
                str("default_text_lang", "默认合成语言",
                        seed != null ? seed.getTextLang() : "zh"),
                str("default_prompt_lang", "默认参考音频语言",
                        seed != null ? seed.getPromptLang() : "zh"),
                bool("seed_voice.enabled", "启用 Seed 音色",
                        seed != null && seed.isEnabled(), false),
                str("seed_voice.name", "Seed 展示名",
                        seed != null ? seed.getName() : "达妮娅"),
                intField("connect_timeout_ms", "连接超时(ms)",
                        gptSovitsProperties.getConnectTimeoutMs(), 500, 60000),
                intField("read_timeout_ms", "读超时(ms)",
                        gptSovitsProperties.getReadTimeoutMs(), 1000, 600000),
                intField("ref_audio.max_size_mb", "参考音频上限(MB)",
                        ref != null ? ref.getMaxSizeMb() : 10, 1, 100)
        );
    }

    @Override
    public Map<String, Object> defaults() {
        Map<String, Object> map = new LinkedHashMap<>();
        for (SettingFieldSchema field : schema()) {
            map.put(field.getKey(), field.getDefaultValue());
        }
        return map;
    }

    @Override
    public void validate(Map<String, Object> items) {
        for (String langKey : List.of("default_text_lang", "default_prompt_lang")) {
            if (items.containsKey(langKey)) {
                String lang = String.valueOf(items.get(langKey)).trim().toLowerCase();
                if (!LANGS.contains(lang)) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR,
                            langKey + " 仅支持: " + String.join(",", LANGS));
                }
            }
        }
        if (items.containsKey("seed_voice.name")) {
            Object name = items.get("seed_voice.name");
            if (name == null || String.valueOf(name).isBlank()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "seed_voice.name 不能为空");
            }
        }
    }

    @Override
    public void onChanged() {
        ttsProxyService.invalidateClient();
    }

    private SettingFieldSchema bool(String key, String label, boolean def, boolean danger) {
        return SettingFieldSchema.builder()
                .key(key).valueType(SiteSettingConstant.TYPE_BOOL).label(label).description(label)
                .defaultValue(def).danger(danger).build();
    }

    private SettingFieldSchema intField(String key, String label, int def, double min, double max) {
        return SettingFieldSchema.builder()
                .key(key).valueType(SiteSettingConstant.TYPE_INT).label(label).description(label)
                .defaultValue(def).min(min).max(max).build();
    }

    private SettingFieldSchema str(String key, String label, String def) {
        return SettingFieldSchema.builder()
                .key(key).valueType(SiteSettingConstant.TYPE_STRING).label(label).description(label)
                .defaultValue(def == null ? "" : def).build();
    }
}
