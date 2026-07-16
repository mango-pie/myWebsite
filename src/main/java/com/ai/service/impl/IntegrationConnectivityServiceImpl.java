package com.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.model.vo.setting.IntegrationTestResultVO;
import com.ai.service.AstrBotChatService;
import com.ai.service.IntegrationConnectivityService;
import com.ai.service.IntegrationCredentialsService;
import com.ai.service.TtsProxyService;
import com.ai.service.knowledge.KnowledgeVectorStoreService;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class IntegrationConnectivityServiceImpl implements IntegrationConnectivityService {

    private static final List<String> ALL_TARGETS = List.of(
            "redis", "vector", "knowledge_ai", "agent", "codegen", "jina", "astrbot", "tts", "minio"
    );

    @Resource
    private IntegrationCredentialsService credentials;
    @Resource
    private AstrBotChatService astrBotChatService;
    @Resource
    private TtsProxyService ttsProxyService;
    @Resource
    private KnowledgeVectorStoreService knowledgeVectorStoreService;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public List<IntegrationTestResultVO> testAll() {
        List<IntegrationTestResultVO> results = new ArrayList<>();
        for (String target : ALL_TARGETS) {
            try {
                results.add(test(target));
            } catch (BusinessException e) {
                results.add(IntegrationTestResultVO.builder()
                        .target(target)
                        .ok(false)
                        .latencyMs(0)
                        .message(e.getMessage())
                        .build());
            } catch (Exception e) {
                results.add(IntegrationTestResultVO.builder()
                        .target(target)
                        .ok(false)
                        .latencyMs(0)
                        .message("探测失败（详情已隐藏）")
                        .build());
            }
        }
        return results;
    }

    @Override
    public IntegrationTestResultVO test(String target) {
        if (StrUtil.isBlank(target)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "target 不能为空");
        }
        String t = target.trim().toLowerCase();
        long start = System.currentTimeMillis();
        try {
            return switch (t) {
                case "redis" -> testRedis();
                case "vector" -> testVector();
                case "knowledge_ai" -> testOpenAiCompat(
                        t, credentials.knowledgeAiBaseUrl(), credentials.knowledgeAiApiKey(), "/models");
                case "agent" -> testOpenAiCompat(
                        t, credentials.agentBaseUrl(), credentials.agentApiKey(), "/models");
                case "codegen" -> testOpenAiCompat(
                        t, credentials.codegenBaseUrl(), credentials.codegenApiKey(), "/models");
                case "jina" -> testJina();
                case "astrbot" -> result(t, astrBotChatService.isAvailable(), start,
                        astrBotChatService.isAvailable() ? "AstrBot 可达" : "AstrBot 不可达");
                case "tts" -> result(t, ttsProxyService.isAvailable(), start,
                        ttsProxyService.isAvailable() ? "GPT-SoVITS 可达" : "GPT-SoVITS 不可达");
                case "minio" -> testMinio();
                default -> throw new BusinessException(ErrorCode.PARAMS_ERROR,
                        "未知 target，支持: " + String.join(", ", ALL_TARGETS));
            };
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            return result(t, false, start, "探测失败（详情已隐藏）");
        }
    }

    private IntegrationTestResultVO testRedis() {
        long start = System.currentTimeMillis();
        if (stringRedisTemplate == null) {
            return result("redis", false, start, "Redis 未配置");
        }
        try {
            var factory = stringRedisTemplate.getConnectionFactory();
            if (factory == null) {
                return result("redis", false, start, "Redis 连接工厂未就绪");
            }
            try (var connection = factory.getConnection()) {
                String pong = connection.ping();
                boolean ok = pong != null && !pong.isBlank();
                return result("redis", ok, start, ok ? "PING 成功" : "PING 失败");
            }
        } catch (Exception e) {
            return result("redis", false, start, "Redis 不可达");
        }
    }

    private IntegrationTestResultVO testVector() {
        long start = System.currentTimeMillis();
        boolean ok = knowledgeVectorStoreService.ping();
        return result("vector", ok, start, ok ? "向量库 SELECT 1 成功" : "向量库不可达");
    }

    private IntegrationTestResultVO testOpenAiCompat(String target, String baseUrl, String apiKey, String path)
            throws Exception {
        if (StrUtil.isBlank(baseUrl)) {
            return result(target, false, System.currentTimeMillis(), "baseUrl 未配置");
        }
        if (StrUtil.isBlank(apiKey)) {
            return result(target, false, System.currentTimeMillis(), "apiKey 未配置");
        }
        long start = System.currentTimeMillis();
        String url = trim(baseUrl) + path;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
        return result(target, ok, start, ok ? "连通成功" : "HTTP " + response.statusCode());
    }

    private IntegrationTestResultVO testJina() throws Exception {
        long start = System.currentTimeMillis();
        String base = credentials.jinaBaseUrl();
        if (StrUtil.isBlank(base)) {
            return result("jina", false, start, "baseUrl 未配置");
        }
        String probe = (base.endsWith("/") ? base : base + "/") + "https://example.com";
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(probe))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "Ai-Backend-Settings-Test/1.0");
        if (StrUtil.isNotBlank(credentials.jinaApiKey())) {
            builder.header("Authorization", "Bearer " + credentials.jinaApiKey());
        }
        HttpResponse<String> response = httpClient.send(builder.GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        boolean ok = response.statusCode() >= 200 && response.statusCode() < 400;
        return result("jina", ok, start, ok ? "Jina 可达" : "HTTP " + response.statusCode());
    }

    private IntegrationTestResultVO testMinio() {
        long start = System.currentTimeMillis();
        if (!credentials.minioEnabled()) {
            return result("minio", false, start, "MinIO 未启用");
        }
        try {
            MinioClient client = MinioClient.builder()
                    .endpoint(credentials.minioEndpoint())
                    .credentials(credentials.minioAccessKey(), credentials.minioSecretKey())
                    .build();
            boolean exists = client.bucketExists(BucketExistsArgs.builder()
                    .bucket(credentials.minioBucketDocuments())
                    .build());
            return result("minio", true, start, exists ? "Bucket 存在" : "连通成功（Bucket 不存在，上传时会创建）");
        } catch (Exception e) {
            return result("minio", false, start, "MinIO 不可达");
        }
    }

    private IntegrationTestResultVO result(String target, boolean ok, long start, String message) {
        return IntegrationTestResultVO.builder()
                .target(target)
                .ok(ok)
                .latencyMs(Math.max(0, System.currentTimeMillis() - start))
                .message(message)
                .build();
    }

    private String trim(String baseUrl) {
        String value = baseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
