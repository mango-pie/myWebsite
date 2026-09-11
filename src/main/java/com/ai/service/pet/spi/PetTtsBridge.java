package com.ai.service.pet.spi;

import com.ai.model.vo.tts.TtsVoiceVO;

import java.util.List;
import java.util.Map;

/**
 * Pet TTS 能力 SPI。由 pet 平台侧定义，tts 模块提供真实实现，
 * tts 关闭时由 {@link NoOpPetTtsBridge} 兜底，避免 {@code PetFacade} 强依赖 tts 的 Service Bean。
 */
public interface PetTtsBridge {

    List<TtsVoiceVO> listVoices();

    byte[] synthesize(String text, Long voiceId, Map<String, Object> extra);

    void requireEnabled();
}
