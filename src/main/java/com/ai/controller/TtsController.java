package com.ai.controller;

import com.ai.annotation.AuthCheck;
import com.ai.common.BaseResponse;
import com.ai.common.DeleteRequest;
import com.ai.common.ResultUtils;
import com.ai.config.GptSovitsProperties;
import com.ai.constant.UserConstant;
import com.ai.exception.ErrorCode;
import com.ai.exception.ThrowUtils;
import com.ai.model.dto.tts.TtsSynthesizeRequest;
import com.ai.model.dto.tts.TtsVoiceSelectRequest;
import com.ai.model.dto.tts.TtsVoiceUpdateRequest;
import com.ai.model.entity.TtsVoiceProfile;
import com.ai.model.vo.tts.TtsConfigVO;
import com.ai.model.vo.tts.TtsHealthVO;
import com.ai.model.vo.tts.TtsVoiceVO;
import com.ai.service.TtsProxyService;
import com.ai.service.TtsVoiceService;
import jakarta.annotation.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/tts")
public class TtsController {

    @Resource
    private TtsProxyService ttsProxyService;

    @Resource
    private TtsVoiceService ttsVoiceService;

    @Resource
    private GptSovitsProperties gptSovitsProperties;

    @GetMapping("/health")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<TtsHealthVO> health() {
        TtsHealthVO vo = new TtsHealthVO();
        boolean available = ttsProxyService.isAvailable();
        vo.setAvailable(available);
        vo.setBaseUrl(gptSovitsProperties.getBaseUrl());
        vo.setMessage(available ? "GPT-SoVITS 服务正常" : "GPT-SoVITS 未启动或不可达");
        return ResultUtils.success(vo);
    }

    @GetMapping("/config")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<TtsConfigVO> config() {
        TtsConfigVO vo = new TtsConfigVO();
        vo.setGptSovitsAvailable(ttsProxyService.isAvailable());
        vo.setRefPreloaded(ttsVoiceService.isRefPreloaded());
        try {
            TtsVoiceProfile def = ttsVoiceService.getDefaultVoice();
            vo.setDefaultVoiceId(def.getId());
            vo.setDefaultVoiceName(def.getName());
        } catch (Exception ignored) {
            vo.setDefaultVoiceId(null);
            vo.setDefaultVoiceName(null);
        }
        return ResultUtils.success(vo);
    }

    @PostMapping("/ref/init")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<Boolean> initRef(@RequestParam(required = false) Long voiceId) {
        ttsVoiceService.initReferAudio(voiceId);
        return ResultUtils.success(true);
    }

    @PostMapping(value = "/synthesize", produces = "audio/wav")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public ResponseEntity<byte[]> synthesize(@RequestBody TtsSynthesizeRequest request) {
        ThrowUtils.throwIf(request == null || request.getText() == null || request.getText().isBlank(),
                ErrorCode.PARAMS_ERROR);

        TtsVoiceProfile voice = ttsVoiceService.getVoiceOrDefault(request.getVoiceId());
        Map<String, Object> extra = buildSynthesizeExtra(request);

        byte[] audio = ttsProxyService.synthesize(request.getText(), voice, extra);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "audio/wav")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"tts.wav\"")
                .body(audio);
    }

    @GetMapping("/voice/list")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<List<TtsVoiceVO>> listVoices() {
        return ResultUtils.success(ttsVoiceService.listVoices());
    }

    @GetMapping("/voice/get")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<TtsVoiceVO> getVoice(Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(ttsVoiceService.getVoiceVO(id));
    }

    @PostMapping("/voice/add")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<Long> addVoice(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam(value = "promptText", required = false) String promptText,
            @RequestParam(value = "promptLang", required = false) String promptLang,
            @RequestParam(value = "textLang", required = false) String textLang) {
        return ResultUtils.success(ttsVoiceService.addVoice(file, name, promptText, promptLang, textLang));
    }

    @PostMapping("/voice/update")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<Boolean> updateVoice(@RequestBody TtsVoiceUpdateRequest request) {
        ThrowUtils.throwIf(request == null || request.getId() == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(ttsVoiceService.updateVoice(request));
    }

    @PostMapping("/voice/delete")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<Boolean> deleteVoice(@RequestBody DeleteRequest request) {
        ThrowUtils.throwIf(request == null || request.getId() == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(ttsVoiceService.deleteVoice(request.getId()));
    }

    @PostMapping("/voice/select")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<Boolean> selectVoice(@RequestBody TtsVoiceSelectRequest request) {
        ThrowUtils.throwIf(request == null || request.getId() == null, ErrorCode.PARAMS_ERROR);
        ttsVoiceService.selectDefaultVoice(request.getId());
        return ResultUtils.success(true);
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
