package com.ai.model.vo.tts;

import lombok.Data;

import java.io.Serializable;

@Data
public class TtsHealthVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Boolean available;

    private String message;

    private String baseUrl;
}
