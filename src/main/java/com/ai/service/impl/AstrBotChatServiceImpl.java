package com.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import cn.hutool.json.JSONObject;
import com.ai.config.AstrBotProperties;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.model.dto.chat.ChatMessageSegment;
import com.ai.model.vo.chat.ChatConfigVO;
import com.ai.service.AstrBotChatService;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AstrBotChatServiceImpl implements AstrBotChatService {

    /** 插件文档：历史净化（思维链 + 尾部情感状态块） */
    private static final Pattern HISTORY_CLEAN_PATTERN = Pattern.compile(
            "(?:```(?:xml|text)?\\s*)?<(?:thought|thinking)>[\\s\\S]*?</(?:thought|thinking)>(?:\\s*```)?"
                    + "|(?:\\n*\\s*【当前情感状态】[\\s\\S]*$)",
            Pattern.CASE_INSENSITIVE
    );

    /** 流式未闭合思维链兜底 */
    private static final Pattern UNCLOSED_THOUGHT_PATTERN = Pattern.compile(
            "(?:```(?:xml|text)?\\s*)?<(?:thought|thinking)>[\\s\\S]*$",
            Pattern.CASE_INSENSITIVE
    );

    /** 插件内联思考（无 XML 标签）：1.感知：...5.更新：surprise:-1回复 */
    private static final Pattern PLUGIN_THINKING_START = Pattern.compile("(?:\\d+\\.)?感知[：:]|\\[感知\\]");
    private static final Pattern UPDATE_STEP_PATTERN = Pattern.compile("(\\d+\\.)?更新[：:]|\\[更新\\]");
    private static final Pattern EMOTION_PREFIX_PATTERN = Pattern.compile(
            "^(?:(?:\\w+|[\\u4e00-\\u9fa5]+)[:+]\\s*[-+]?\\d+[,，\\s]*)+"
    );

    @Resource
    private AstrBotProperties astrBotProperties;

    @Resource(name = "astrBotWebClient")
    private WebClient astrBotWebClient;

    @Override
    public boolean isAvailable() {
        try {
            // 轻量探测 configs 接口
            astrBotWebClient.get()
                    .uri("/api/v1/configs")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(5));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<ChatConfigVO> listConfigs() {
        try {
            String raw = astrBotWebClient.get()
                    .uri("/api/v1/configs")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(10));
            return parseConfigsResponse(raw);
        } catch (WebClientResponseException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "拉取 AstrBot 配置失败: " + parseErrorMessage(e));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "AstrBot 服务不可用: " + e.getMessage());
        }
    }

    private List<ChatConfigVO> parseConfigsResponse(String raw) {
        if (StrUtil.isBlank(raw)) {
            return Collections.emptyList();
        }
        if (!JSONUtil.isTypeJSON(raw)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "AstrBot 配置响应格式异常");
        }

        Object parsed = JSONUtil.parse(raw);
        if (parsed instanceof JSONArray jsonArray) {
            return jsonArrayToConfigList(jsonArray);
        }
        if (!(parsed instanceof JSONObject json)) {
            return Collections.emptyList();
        }

        String status = json.getStr("status");
        if (StrUtil.isNotBlank(status) && !"ok".equalsIgnoreCase(status)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "AstrBot 配置接口返回异常: " + StrUtil.blankToDefault(json.getStr("message"), status));
        }

        JSONObject data = json.getJSONObject("data");
        if (data != null && data.containsKey("configs")) {
            return jsonArrayToConfigList(data.getJSONArray("configs"));
        }
        if (json.containsKey("configs")) {
            return jsonArrayToConfigList(json.getJSONArray("configs"));
        }
        return Collections.emptyList();
    }

    private List<ChatConfigVO> jsonArrayToConfigList(JSONArray array) {
        if (array == null || array.isEmpty()) {
            return Collections.emptyList();
        }
        List<ChatConfigVO> result = new ArrayList<>(array.size());
        for (Object item : array) {
            if (item instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> configMap = (Map<String, Object>) map;
                result.add(mapToChatConfigVO(configMap));
            } else if (item instanceof JSONObject obj) {
                result.add(mapToChatConfigVO(obj));
            }
        }
        return result;
    }

    private ChatConfigVO mapToChatConfigVO(Map<String, Object> raw) {
        ChatConfigVO vo = new ChatConfigVO();
        if (raw == null) {
            return vo;
        }
        Object id = raw.getOrDefault("id", raw.get("config_id"));
        vo.setId(id != null ? String.valueOf(id) : null);

        Object name = raw.getOrDefault("name", raw.getOrDefault("title", vo.getId()));
        vo.setName(name != null ? String.valueOf(name) : "default");

        Object desc = raw.get("description");
        if (desc == null && Boolean.TRUE.equals(raw.get("is_default"))) {
            desc = "默认预设";
        }
        vo.setDescription(desc != null ? String.valueOf(desc) : null);

        vo.setRaw(raw);
        return vo;
    }

    @Override
    public Flux<String> streamChat(String username, String sessionId, String configId, String message,
                                   List<ChatMessageSegment> segments) {
        if (StrUtil.isBlank(username)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "username 不能为空");
        }
        if (StrUtil.isBlank(sessionId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "sessionId 不能为空");
        }

        Object messagePayload = buildMessagePayload(message, segments);

        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("session_id", sessionId);
        body.put("message", messagePayload);
        body.put("enable_streaming", true);
        if (StrUtil.isNotBlank(configId)) {
            body.put("config_id", configId);
        }

        return astrBotWebClient.post()
                .uri("/api/v1/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(ServerSentEvent.class)
                .<String>handle((sse, sink) -> {
                    String chunk = extractDisplayTextFromSse(sse);
                    if (StrUtil.isNotBlank(chunk)) {
                        sink.next(chunk);
                    }
                })
                .onErrorMap(WebClientResponseException.class, e -> {
                    String msg = parseErrorMessage(e);
                    return new BusinessException(ErrorCode.OPERATION_ERROR, "AstrBot 回复失败: " + msg);
                })
                .onErrorMap(Throwable.class, e -> {
                    if (e instanceof BusinessException) {
                        return (BusinessException) e;
                    }
                    return new BusinessException(ErrorCode.OPERATION_ERROR, "AstrBot 调用异常: " + e.getMessage());
                });
    }

    private Object buildMessagePayload(String message, List<ChatMessageSegment> segments) {
        if (segments != null && !segments.isEmpty()) {
            List<Map<String, Object>> chain = buildMessageChain(segments);
            if (!chain.isEmpty()) {
                return chain;
            }
        }
        if (StrUtil.isBlank(message)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "消息不能为空");
        }
        return message;
    }

    private List<Map<String, Object>> buildMessageChain(List<ChatMessageSegment> segments) {
        List<Map<String, Object>> chain = new ArrayList<>();
        for (ChatMessageSegment segment : segments) {
            if (segment == null || StrUtil.isBlank(segment.getType())) {
                continue;
            }
            String type = segment.getType().trim().toLowerCase();
            switch (type) {
                case "plain" -> {
                    if (StrUtil.isNotBlank(segment.getText())) {
                        chain.add(Map.of("type", "plain", "text", segment.getText().trim()));
                    }
                }
                case "image", "file", "record", "video" -> {
                    if (StrUtil.isNotBlank(segment.getAttachmentId())) {
                        Map<String, Object> item = new HashMap<>();
                        item.put("type", type);
                        item.put("attachment_id", segment.getAttachmentId().trim());
                        chain.add(item);
                    }
                }
                default -> {
                }
            }
        }
        return chain;
    }

    @Override
    public String cleanDisplayContent(String raw) {
        return cleanDisplayText(raw);
    }

    private String extractDisplayTextFromSse(ServerSentEvent sse) {
        Map<String, Object> event = resolveAstrBotEvent(sse);
        if (event == null || event.isEmpty()) {
            return null;
        }

        String type = optString(event.get("type"));
        if (!"complete".equals(type)) {
            return null;
        }
        return cleanDisplayText(stringifyEventData(event.get("data")));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveAstrBotEvent(ServerSentEvent sse) {
        if (sse == null) {
            return null;
        }
        Object data = sse.data();
        if (data == null) {
            return null;
        }
        if (data instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (data instanceof JSONObject json) {
            return json;
        }
        if (data instanceof String raw) {
            return parseAstrBotEventString(raw);
        }
        return null;
    }

    private Map<String, Object> parseAstrBotEventString(String raw) {
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        if (!JSONUtil.isTypeJSON(trimmed)) {
            return Map.of("type", "plain", "data", trimmed);
        }
        try {
            return JSONUtil.parseObj(trimmed);
        } catch (Exception ignore) {
            return null;
        }
    }

    private String cleanDisplayText(String text) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        String cleaned = HISTORY_CLEAN_PATTERN.matcher(text.trim()).replaceAll("").trim();
        cleaned = UNCLOSED_THOUGHT_PATTERN.matcher(cleaned).replaceAll("").trim();
        cleaned = cleaned.replaceAll("(?i)\\n*\\s*【当前情感状态】[\\s\\S]*$", "").trim();
        cleaned = stripInlinePluginThinking(cleaned);
        return StrUtil.isNotBlank(cleaned) ? cleaned : null;
    }

    private String stripInlinePluginThinking(String text) {
        if (StrUtil.isBlank(text) || !PLUGIN_THINKING_START.matcher(text).find()) {
            return text;
        }
        Matcher matcher = UPDATE_STEP_PATTERN.matcher(text);
        int contentStart = -1;
        while (matcher.find()) {
            contentStart = matcher.end();
        }
        if (contentStart < 0) {
            return null;
        }
        String remainder = text.substring(contentStart).trim();
        Matcher emotionMatcher = EMOTION_PREFIX_PATTERN.matcher(remainder);
        if (emotionMatcher.find()) {
            remainder = remainder.substring(emotionMatcher.end()).trim();
        }
        return StrUtil.isNotBlank(remainder) ? remainder : null;
    }

    private String stringifyEventData(Object data) {
        if (data == null) {
            return null;
        }
        if (data instanceof String text) {
            return text;
        }
        if (data instanceof Number || data instanceof Boolean) {
            return data.toString();
        }
        return null;
    }

    private String optString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String parseErrorMessage(WebClientResponseException e) {
        String raw = e.getResponseBodyAsString();
        if (StrUtil.isNotBlank(raw) && JSONUtil.isTypeJSON(raw)) {
            try {
                JSONObject json = JSONUtil.parseObj(raw);
                if (json.containsKey("message")) {
                    return json.getStr("message");
                }
                if (json.containsKey("error")) {
                    return json.getStr("error");
                }
            } catch (Exception ignore) {
            }
        }
        return StrUtil.blankToDefault(raw, "HTTP " + e.getStatusCode().value());
    }
}