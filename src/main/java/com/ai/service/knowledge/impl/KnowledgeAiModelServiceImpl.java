package com.ai.service.knowledge.impl;

import com.ai.constant.AiUsageSceneConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.model.dto.ops.AiUsageRecord;
import com.ai.ops.AiUsageCallContext;
import com.ai.service.AiUsageLogService;
import com.ai.service.IntegrationCredentialsService;
import com.ai.service.knowledge.KnowledgeAiModelService;
import com.ai.setting.IntegrationClientCache;
import com.ai.setting.runtime.KnowledgeRuntimeSettings;
import com.ai.utils.AiUsageTokenExtractor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

@Slf4j
@Service
public class KnowledgeAiModelServiceImpl implements KnowledgeAiModelService {

    private final ObjectMapper objectMapper;

    @Resource
    private IntegrationCredentialsService credentials;

    @Resource
    private IntegrationClientCache integrationClientCache;

    @Resource
    private KnowledgeRuntimeSettings knowledgeRuntimeSettings;

    @Resource
    private AiUsageLogService aiUsageLogService;

    private volatile long cachedVersion = -1;
    private volatile RestClient cachedRestClient;
    private volatile String cachedFingerprint;
    private volatile HttpClient cachedHttpClient;
    private volatile int cachedHttpTimeout = -1;

    public KnowledgeAiModelServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String chat(String prompt) {
        return chat(null, prompt, null, null);
    }

    @Override
    public String chat(String systemPrompt, String userPrompt, Double temperature, Integer maxTokens) {
        ensureApiKey();
        long startNs = System.nanoTime();
        try {
            JsonNode root = restClient().post()
                    .uri("/chat/completions")
                    .body(buildChatBody(systemPrompt, userPrompt, false, temperature, maxTokens))
                    .retrieve()
                    .body(JsonNode.class);
            String content = root == null ? null : root.path("choices").path(0).path("message").path("content").asText(null);
            if (!StringUtils.hasText(content)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "ChatModel 响应为空");
            }
            AiUsageTokenExtractor.Tokens tokens = AiUsageTokenExtractor.fromOpenAiUsage(
                    root == null ? null : root.path("usage"));
            recordUsage(true, startNs, tokens, null);
            return content;
        } catch (BusinessException e) {
            recordUsage(false, startNs, AiUsageTokenExtractor.Tokens.empty(), e.getMessage());
            throw e;
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            log.error("Call knowledge chat model failed", e);
            String detail = root instanceof java.net.http.HttpTimeoutException
                    || rootClassNameContains(root, "Timeout")
                    ? "（请求超时，请稍后重试或增大 knowledge.ai.timeout_seconds）"
                    : "";
            String message = "调用知识库 ChatModel 失败" + detail;
            recordUsage(false, startNs, AiUsageTokenExtractor.Tokens.empty(), message);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, message);
        }
    }

    private boolean rootClassNameContains(Throwable root, String keyword) {
        return root != null && root.getClass().getName().contains(keyword);
    }

    @Override
    public void streamChat(String prompt, Consumer<String> onDelta) {
        ensureApiKey();
        int timeoutSeconds = effectiveTimeoutSeconds();
        long startNs = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimBaseUrl(credentials.knowledgeAiBaseUrl()) + "/chat/completions"))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + credentials.knowledgeAiApiKey())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.ACCEPT, "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(buildChatBody(null, prompt, true, null, null)),
                            StandardCharsets.UTF_8))
                    .build();
            HttpResponse<java.io.InputStream> response = httpClient().send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String body = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "流式 ChatModel 调用失败：" + body);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    handleStreamLine(line, onDelta);
                }
            }
            recordUsage(true, startNs, AiUsageTokenExtractor.Tokens.empty(), null);
        } catch (BusinessException e) {
            recordUsage(false, startNs, AiUsageTokenExtractor.Tokens.empty(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Call knowledge streaming chat model failed", e);
            String message = "调用知识库流式 ChatModel 失败";
            recordUsage(false, startNs, AiUsageTokenExtractor.Tokens.empty(), message);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, message);
        }
    }

    @Override
    public float[] embed(String text) {
        List<float[]> result = embed(List.of(text));
        if (result.isEmpty()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "Embedding 结果为空");
        }
        return result.get(0);
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        ensureApiKey();
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", knowledgeRuntimeSettings.embeddingModel());
            body.put("input", texts);
            body.put("dimensions", knowledgeRuntimeSettings.embeddingDimension());
            JsonNode root = restClient().post()
                    .uri("/embeddings")
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode data = root == null ? null : root.path("data");
            if (data == null || !data.isArray()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "Embedding 响应为空");
            }
            return java.util.stream.StreamSupport.stream(data.spliterator(), false)
                    .sorted(Comparator.comparing(node -> node.path("index").asInt(0)))
                    .map(node -> toFloatArray(node.path("embedding")))
                    .toList();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Call knowledge embedding model failed", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "调用知识库 Embedding 模型失败");
        }
    }

    @Override
    public String getChatModelName() {
        return knowledgeRuntimeSettings.chatModel();
    }

    private RestClient restClient() {
        String baseUrl = trimBaseUrl(credentials.knowledgeAiBaseUrl());
        String apiKey = credentials.knowledgeAiApiKey();
        int timeoutSeconds = effectiveTimeoutSeconds();
        String fingerprint = baseUrl + "|" + apiKey + "|t=" + timeoutSeconds;
        long version = integrationClientCache.version();
        if (cachedRestClient != null && version == cachedVersion && fingerprint.equals(cachedFingerprint)) {
            return cachedRestClient;
        }
        synchronized (this) {
            if (cachedRestClient != null && version == cachedVersion && fingerprint.equals(cachedFingerprint)) {
                return cachedRestClient;
            }
            HttpClient jdkClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(Math.min(30, timeoutSeconds)))
                    .build();
            JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(jdkClient);
            requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
            RestClient client = RestClient.builder()
                    .baseUrl(baseUrl)
                    .requestFactory(requestFactory)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();
            cachedRestClient = client;
            cachedFingerprint = fingerprint;
            cachedVersion = version;
            return client;
        }
    }

    private HttpClient httpClient() {
        int timeoutSeconds = effectiveTimeoutSeconds();
        if (cachedHttpClient != null && cachedHttpTimeout == timeoutSeconds) {
            return cachedHttpClient;
        }
        synchronized (this) {
            if (cachedHttpClient != null && cachedHttpTimeout == timeoutSeconds) {
                return cachedHttpClient;
            }
            cachedHttpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(Math.min(30, timeoutSeconds)))
                    .build();
            cachedHttpTimeout = timeoutSeconds;
            return cachedHttpClient;
        }
    }

    private int effectiveTimeoutSeconds() {
        return Math.max(30, knowledgeRuntimeSettings.timeoutSeconds());
    }

    private Map<String, Object> buildChatBody(String systemPrompt, String userPrompt, boolean stream,
                                              Double temperature, Integer maxTokens) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", knowledgeRuntimeSettings.chatModel());
        body.put("stream", stream);
        body.put("temperature", temperature != null ? temperature : knowledgeRuntimeSettings.temperature());
        body.put("max_tokens", maxTokens != null ? maxTokens : knowledgeRuntimeSettings.maxTokens());
        List<Map<String, String>> messages = new ArrayList<>();
        if (StringUtils.hasText(systemPrompt)) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userPrompt == null ? "" : userPrompt));
        body.put("messages", messages);
        return body;
    }

    private void handleStreamLine(String line, Consumer<String> onDelta) throws Exception {
        if (line == null || line.isBlank() || !line.startsWith("data:")) {
            return;
        }
        String data = line.substring(5).trim();
        if ("[DONE]".equals(data)) {
            return;
        }
        JsonNode root = objectMapper.readTree(data);
        String delta = root.path("choices").path(0).path("delta").path("content").asText(null);
        if (StringUtils.hasText(delta)) {
            onDelta.accept(delta);
        }
    }

    private float[] toFloatArray(JsonNode embedding) {
        if (embedding == null || !embedding.isArray()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "Embedding 数据格式错误");
        }
        float[] result = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            result[i] = (float) embedding.get(i).asDouble();
        }
        return result;
    }

    private void ensureApiKey() {
        if (!StringUtils.hasText(credentials.knowledgeAiApiKey())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "请配置知识库 AI API Key（设置中心或 KNOWLEDGE_AI_API_KEY）");
        }
    }

    private void recordUsage(boolean success, long startNs, AiUsageTokenExtractor.Tokens tokens, String errorMessage) {
        AiUsageCallContext.Holder ctx = AiUsageCallContext.get();
        if (ctx == null) {
            return;
        }
        long elapsedMs = Math.max(0L, (System.nanoTime() - startNs) / 1_000_000L);
        aiUsageLogService.record(AiUsageRecord.builder()
                .userId(ctx.userId())
                .scene(ctx.scene())
                .conversationId(ctx.conversationId())
                .modelName(getChatModelName())
                .promptTokens(tokens.promptTokens())
                .completionTokens(tokens.completionTokens())
                .totalTokens(tokens.totalTokens())
                .responseTimeMs(elapsedMs)
                .status(success ? AiUsageSceneConstant.STATUS_SUCCESS : AiUsageSceneConstant.STATUS_ERROR)
                .errorMessage(errorMessage)
                .requestSummary(ctx.requestSummary())
                .build());
    }

    private String trimBaseUrl(String baseUrl) {
        String value = Objects.requireNonNullElse(baseUrl, "").trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
