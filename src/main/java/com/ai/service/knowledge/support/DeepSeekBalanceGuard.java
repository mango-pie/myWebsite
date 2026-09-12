package com.ai.service.knowledge.support;

import com.ai.config.ConditionalOnModule;

import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.service.IntegrationCredentialsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 供应商余额熔断（DeepSeek 官方）：余额不足时拒绝新任务，避免任务静默失败、白烧配额。
 * 开关 ingest.balance_check_enabled；检查失败时 fail-open（不阻断采集）。
 */
@Slf4j
@ConditionalOnModule("knowledge")
@Component
public class DeepSeekBalanceGuard {

    /** 余额低于该值（人民币）时熔断新任务 */
    private static final double MIN_BALANCE = 0.5;
    /** 检查结果缓存 10 分钟，避免每次提交都打余额接口 */
    private static final long CACHE_MS = 10 * 60 * 1000;

    private final AtomicReference<Cached> cache = new AtomicReference<>();

    @Resource
    private IntegrationCredentialsService credentials;

    @Resource
    private ObjectMapper objectMapper;

    /** 余额不足或不可用返回错误；未配置 / 检查失败返回 null（放行） */
    public String checkInsufficient() {
        Cached current = cache.get();
        long now = System.currentTimeMillis();
        if (current != null && now - current.checkedAt() < CACHE_MS) {
            return current.reason();
        }
        String reason = doCheck();
        cache.set(new Cached(now, reason));
        return reason;
    }

    private String doCheck() {
        String apiKey = credentials.deepseekApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        try {
            String base = credentials.deepseekBaseUrl();
            if (base == null || base.isBlank()) {
                return null;
            }
            base = base.replaceAll("/+$", "");
            int idx = base.toLowerCase().lastIndexOf("/anthropic");
            if (idx >= 0) {
                base = base.substring(0, idx);
            }
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/user/balance"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.debug("DeepSeek balance query failed: {}", response.statusCode());
                return null;
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (root.has("is_available") && !root.path("is_available").asBoolean(false)) {
                return "DeepSeek 余额不可用，已暂停新任务，请充值后重试";
            }
            double total = 0;
            JsonNode infos = root.path("balance_infos");
            if (infos.isArray()) {
                for (JsonNode info : infos) {
                    total += info.path("total_balance").asDouble(0);
                }
            } else if (root.has("balance")) {
                total = root.path("balance").asDouble(0);
            }
            if (total < MIN_BALANCE) {
                return "DeepSeek 余额不足（剩余 " + total + " 元），已暂停新任务，请充值后重试";
            }
            return null;
        } catch (Exception e) {
            log.debug("DeepSeek balance check failed, fail-open: {}", e.getMessage());
            return null;
        }
    }

    public void assertCanIngest() {
        String reason = checkInsufficient();
        if (reason != null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, reason);
        }
    }

    private record Cached(long checkedAt, String reason) {
    }
}
