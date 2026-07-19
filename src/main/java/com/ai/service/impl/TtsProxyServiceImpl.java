package com.ai.service.impl;

import com.ai.config.ConditionalOnModule;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.model.entity.TtsVoiceProfile;
import com.ai.service.IntegrationCredentialsService;
import com.ai.service.TtsProxyService;
import com.ai.setting.runtime.TtsRuntimeSettings;
import com.ai.utils.TtsPathUtils;
import jakarta.annotation.Resource;
import org.springframework.boot.web.client.RestTemplateBuilder;
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
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@ConditionalOnModule("tts")
@Service
public class TtsProxyServiceImpl implements TtsProxyService {

    @Resource
    private IntegrationCredentialsService credentials;

    @Resource
    private TtsRuntimeSettings ttsRuntimeSettings;

    @Resource
    private RestTemplateBuilder restTemplateBuilder;

    private volatile RestTemplate client;
    private volatile int cachedConnectMs = -1;
    private volatile int cachedReadMs = -1;

    private RestTemplate client() {
        int connectMs = Math.max(500, ttsRuntimeSettings.connectTimeoutMs());
        int readMs = Math.max(1000, ttsRuntimeSettings.readTimeoutMs());
        if (client == null || connectMs != cachedConnectMs || readMs != cachedReadMs) {
            synchronized (this) {
                if (client == null || connectMs != cachedConnectMs || readMs != cachedReadMs) {
                    client = restTemplateBuilder
                            .connectTimeout(Duration.ofMillis(connectMs))
                            .readTimeout(Duration.ofMillis(readMs))
                            .build();
                    cachedConnectMs = connectMs;
                    cachedReadMs = readMs;
                }
            }
        }
        return client;
    }

    @Override
    public void invalidateClient() {
        synchronized (this) {
            client = null;
            cachedConnectMs = -1;
            cachedReadMs = -1;
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            client().getForEntity(credentials.ttsBaseUrl(), String.class);
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
        URI uri = UriComponentsBuilder.fromHttpUrl(credentials.ttsBaseUrl())
                .path("/set_refer_audio")
                .queryParam("refer_audio_path", path)
                .build()
                .encode()
                .toUri();
        try {
            ResponseEntity<String> response = client().getForEntity(uri, String.class);
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
        body.put("text_lang", StrUtil.blankToDefault(voice.getTextLang(), ttsRuntimeSettings.defaultTextLang()));
        body.put("ref_audio_path", TtsPathUtils.normalizePath(voice.getRefAudioPath()));
        body.put("prompt_text", voice.getPromptText());
        body.put("prompt_lang", StrUtil.blankToDefault(voice.getPromptLang(), ttsRuntimeSettings.defaultPromptLang()));
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

        URI uri = UriComponentsBuilder.fromHttpUrl(credentials.ttsBaseUrl())
                .path("/tts")
                .build()
                .toUri();

        try {
            ResponseEntity<byte[]> response = client().exchange(
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
        String body = e.getResponseBodyAsString();
        if (StrUtil.isBlank(body)) {
            return "TTS 请求失败: " + e.getStatusCode();
        }
        try {
            JSONObject json = JSONUtil.parseObj(body);
            String message = json.getStr("message");
            if (StrUtil.isNotBlank(message)) {
                return message;
            }
            String detail = json.getStr("detail");
            if (StrUtil.isNotBlank(detail)) {
                return detail;
            }
        } catch (Exception ignored) {
            // keep raw
        }
        return body.length() > 300 ? body.substring(0, 300) : body;
    }
}
