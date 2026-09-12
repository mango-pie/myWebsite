package com.ai.controller;

import com.ai.common.BaseResponse;
import com.ai.common.ResultUtils;
import com.ai.config.ConditionalOnModule;
import com.ai.exception.ErrorCode;
import com.ai.exception.ThrowUtils;
import com.ai.model.dto.tts.TtsSynthesizeRequest;
import com.ai.model.entity.TtsVoiceProfile;
import com.ai.model.vo.tts.TtsVoiceVO;
import com.ai.service.TtsProxyService;
import com.ai.service.TtsVoiceService;
import com.ai.service.UserService;
import com.ai.setting.runtime.TtsRuntimeSettings;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * EchoBot TTS 桥接：登录用户或 Device Token 可用（不要求 administrator）。
 * 管理端音色 CRUD 仍在 /tts/*。
 */
@ConditionalOnModule("tts")
@RestController
@RequestMapping("/pet/tts")
public class PetTtsController {

    private static final int MAX_TEXT_CHARS = 800;

    @Resource
    private TtsProxyService ttsProxyService;

    @Resource
    private TtsVoiceService ttsVoiceService;

    @Resource
    private TtsRuntimeSettings ttsRuntimeSettings;

    @Resource
    private UserService userService;

    @GetMapping("/voices")
    public BaseResponse<List<TtsVoiceVO>> voices(HttpServletRequest request) {
        userService.getLoginUser(request);
        requireEnabled();
        return ResultUtils.success(ttsVoiceService.listVoices());
    }

    @PostMapping(value = "/synthesize", produces = "audio/wav")
    public ResponseEntity<byte[]> synthesize(@RequestBody TtsSynthesizeRequest body, HttpServletRequest request) {
        userService.getLoginUser(request);
        requireEnabled();
        ThrowUtils.throwIf(body == null || body.getText() == null || body.getText().isBlank(), ErrorCode.PARAMS_ERROR);
        String text = body.getText().trim();
        ThrowUtils.throwIf(text.length() > MAX_TEXT_CHARS, ErrorCode.PARAMS_ERROR, "文本过长（最多 " + MAX_TEXT_CHARS + " 字）");

        TtsVoiceProfile voice = ttsVoiceService.getVoiceOrDefault(body.getVoiceId());
        Map<String, Object> extra = new HashMap<>();
        if (body.getTextLang() != null) extra.put("text_lang", body.getTextLang());
        if (body.getSpeedFactor() != null) extra.put("speed_factor", body.getSpeedFactor());
        if (body.getTextSplitMethod() != null) extra.put("text_split_method", body.getTextSplitMethod());
        if (body.getStreamingMode() != null) extra.put("streaming_mode", body.getStreamingMode());
        if (body.getMediaType() != null) extra.put("media_type", body.getMediaType());

        byte[] audio = ttsProxyService.synthesize(text, voice, extra);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "audio/wav")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"pet-tts.wav\"")
                .body(audio);
    }

    private void requireEnabled() {
        ThrowUtils.throwIf(!ttsRuntimeSettings.enabled(), ErrorCode.OPERATION_ERROR, "TTS 已关闭");
        ThrowUtils.throwIf(!ttsProxyService.isAvailable(), ErrorCode.OPERATION_ERROR, "TTS 服务不可用");
    }
}
