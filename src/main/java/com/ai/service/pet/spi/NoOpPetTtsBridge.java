package com.ai.service.pet.spi;

import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.model.vo.tts.TtsVoiceVO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * tts 模块关闭时的兜底实现；与 PetTtsBridgeImpl 互斥（tts 开时只注册后者）。
 */
@Component
@ConditionalOnProperty(name = "app.modules.tts", havingValue = "false")
public class NoOpPetTtsBridge implements PetTtsBridge {

    @Override
    public List<TtsVoiceVO> listVoices() {
        return Collections.emptyList();
    }

    @Override
    public byte[] synthesize(String text, Long voiceId, Map<String, Object> extra) {
        throw disabled();
    }

    @Override
    public void requireEnabled() {
        throw disabled();
    }

    private BusinessException disabled() {
        return new BusinessException(ErrorCode.OPERATION_ERROR, "tts 模块未启用");
    }
}
