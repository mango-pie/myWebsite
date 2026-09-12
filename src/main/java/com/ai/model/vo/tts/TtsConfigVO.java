package com.ai.model.vo.tts;

import lombok.Data;

import java.io.Serializable;

@Data
public class TtsConfigVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long defaultVoiceId;

    private String defaultVoiceName;

    private Boolean gptSovitsAvailable;

    private Boolean refPreloaded;
}
