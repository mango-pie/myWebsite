package com.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ai.config.GptSovitsProperties;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.model.entity.TtsVoiceProfile;
import com.ai.service.TtsProxyService;
import com.ai.utils.TtsPathUtils;
import jakarta.annotation.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@Service
public class TtsProxyServiceImpl implements TtsProxyService {

    @Resource
    private GptSovitsProperties gptSovitsProperties;

    @Resource
    private RestTemplate gptSovitsRestTemplate;

    @Override
    public boolean isAvailable() {
        try {
            gptSovitsRestTemplate.getForEntity(gptSovitsProperties.getBaseUrl(), String.class);
            return true;
        } catch (ResourceAccessException e) {
            return false;
        } catch (HttpStatusCodeException e) {
            return true;
        }
    }

    @Override
    public void preloadReferAudio(String refAudioPath) {
        String path = TtsPathUtils.normalizePath(refAudioPath);
        if (StrUtil.isBlank(path)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参考音频路径为空");
        }
        URI uri = UriComponentsBuilder.fromHttpUrl(gptSovitsProperties.getBaseUrl())
                .path("/set_refer_audio")
                .queryParam("refer_audio_path", path)
                .build()
                .encode()
                .toUri();
        try {
            ResponseEntity<String> response = gptSovitsRestTemplate.getForEntity(uri, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "预加载参考音频失败");
            }
        } catch (HttpStatusCodeException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, parseErrorMessage(e));
        } catch (ResourceAccessException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "GPT-SoVITS 服务不可用，请先启动 api模式.bat");
        }
    }

    @Override
    public byte[] synthesize(String text, TtsVoiceProfile voice, Map<String, Object> extra) {
        if (StrUtil.isBlank(text)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "text 不能为空");
        }
        if (voice == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未配置可用音色");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("text", text.trim());
        body.put("text_lang", StrUtil.blankToDefault(voice.getTextLang(), "zh"));
        body.put("ref_audio_path", TtsPathUtils.normalizePath(voice.getRefAudioPath()));
        body.put("prompt_text", voice.getPromptText());
        body.put("prompt_lang", StrUtil.blankToDefault(voice.getPromptLang(), "zh"));
        body.put("media_type", "wav");
        body.put("parallel_infer", true);
        body.put("text_split_method", "cut0");
        body.put("streaming_mode", false);

        if (extra != null) {
            extra.forEach((k, v) -> {
                if (v != null) {
                    body.put(k, v);
                }
            });
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        URI uri = UriComponentsBuilder.fromHttpUrl(gptSovitsProperties.getBaseUrl())
                .path("/tts")
                .build()
                .toUri();

        try {
            ResponseEntity<byte[]> response = gptSovitsRestTemplate.exchange(
                    uri, HttpMethod.POST, entity, byte[].class);
            byte[] audio = response.getBody();
            if (audio == null || audio.length == 0) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "TTS 返回空音频");
            }
            return audio;
        } catch (HttpStatusCodeException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, parseErrorMessage(e));
        } catch (ResourceAccessException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "GPT-SoVITS 服务不可用，请先启动 api模式.bat");
        }
    }

    private String parseErrorMessage(HttpStatusCodeException e) {
        String raw = e.getResponseBodyAsString();
        if (StrUtil.isNotBlank(raw) && JSONUtil.isTypeJSON(raw)) {
            JSONObject json = JSONUtil.parseObj(raw);
            if (json.containsKey("message")) {
                return json.getStr("message");
            }
        }
        return StrUtil.blankToDefault(raw, "TTS 合成失败: HTTP " + e.getStatusCode().value());
    }
}
