package com.ai.controller;

import com.ai.common.BaseResponse;
import com.ai.common.ResultUtils;
import com.ai.config.ConditionalOnModule;
import com.ai.exception.ErrorCode;
import com.ai.exception.ThrowUtils;
import com.ai.model.dto.tts.TtsSynthesizeRequest;
import com.ai.model.vo.tts.TtsVoiceVO;
import com.ai.service.UserService;
import com.ai.service.pet.PetFacade;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pet TTS 能力接口。仅在 tts 模块启用时注册。
 * <p>
 * 鉴权走 {@link UserService#getLoginUser} 解析出的身份（当前为 Session）。
 * 后续 Device Bearer 过滤器可写入同一登录上下文，本 Controller 无需改 Session 判断。
 * 设备绑定管理（仅 Session）不在本 PR 范围。
 */
@ConditionalOnModule("tts")
@RestController
@RequestMapping("/pet/tts")
public class PetTtsController {

    private static final int MAX_TEXT_CHARS = 800;

    @Resource
    private PetFacade petFacade;

    @Resource
    private UserService userService;

    @GetMapping("/voices")
    public BaseResponse<List<TtsVoiceVO>> listVoices(HttpServletRequest request) {
        userService.getLoginUser(request);
        return ResultUtils.success(petFacade.ttsVoices());
    }

    @PostMapping(value = "/synthesize", produces = "audio/wav")
    public ResponseEntity<byte[]> synthesize(@RequestBody TtsSynthesizeRequest request,
                                             HttpServletRequest httpRequest) {
        userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(request == null || request.getText() == null || request.getText().isBlank(),
                ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getText().length() > MAX_TEXT_CHARS,
                ErrorCode.PARAMS_ERROR, "文本长度不能超过 " + MAX_TEXT_CHARS + " 字");

        petFacade.requireEnabled();
        byte[] audio = petFacade.ttsSynthesize(request.getText(), request.getVoiceId(), buildSynthesizeExtra(request));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "audio/wav")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"pet-tts.wav\"")
                .body(audio);
    }

    private Map<String, Object> buildSynthesizeExtra(TtsSynthesizeRequest request) {
        Map<String, Object> extra = new HashMap<>();
        if (request.getTextLang() != null) {
            extra.put("text_lang", request.getTextLang());
        }
        if (request.getSpeedFactor() != null) {
            extra.put("speed_factor", request.getSpeedFactor());
        }
        if (request.getTextSplitMethod() != null) {
            extra.put("text_split_method", request.getTextSplitMethod());
        }
        if (request.getStreamingMode() != null) {
            extra.put("streaming_mode", request.getStreamingMode());
        }
        if (request.getMediaType() != null) {
            extra.put("media_type", request.getMediaType());
        }
        return extra;
    }
}
