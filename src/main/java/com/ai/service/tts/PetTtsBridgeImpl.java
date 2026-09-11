package com.ai.service.tts;

import com.ai.config.ConditionalOnModule;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.model.entity.TtsVoiceProfile;
import com.ai.model.vo.tts.TtsVoiceVO;
import com.ai.service.TtsProxyService;
import com.ai.service.TtsVoiceService;
import com.ai.service.pet.spi.PetTtsBridge;
import com.ai.setting.runtime.TtsRuntimeSettings;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * tts 模块提供的 {@link PetTtsBridge} 真实实现，仅在 tts 模块启用时注册。
 */
@ConditionalOnModule("tts")
@Component
public class PetTtsBridgeImpl implements PetTtsBridge {

    @Resource
    private TtsVoiceService ttsVoiceService;

    @Resource
    private TtsProxyService ttsProxyService;

    @Resource
    private TtsRuntimeSettings ttsRuntimeSettings;

    @Override
    public List<TtsVoiceVO> listVoices() {
        return ttsVoiceService.listVoices();
    }

    @Override
    public byte[] synthesize(String text, Long voiceId, Map<String, Object> extra) {
        TtsVoiceProfile voice = ttsVoiceService.getVoiceOrDefault(voiceId);
        return ttsProxyService.synthesize(text, voice, extra);
    }

    @Override
    public void requireEnabled() {
        if (!ttsRuntimeSettings.enabled()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "TTS 已关闭，请在全站设置中开启");
        }
        if (!ttsProxyService.isAvailable()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "GPT-SoVITS 未启动或不可达");
        }
    }
}
