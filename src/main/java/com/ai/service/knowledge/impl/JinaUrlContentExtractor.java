package com.ai.service.knowledge.impl;

import com.ai.config.ConditionalOnModule;

import cn.hutool.core.util.StrUtil;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.service.IntegrationCredentialsService;
import com.ai.service.knowledge.ContentExtractor;
import com.ai.service.knowledge.support.UrlSafetyValidator;
import com.ai.setting.runtime.KnowledgeRuntimeSettings;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ConditionalOnModule("knowledge")
@Service
public class JinaUrlContentExtractor implements ContentExtractor {

    private static final Pattern GITHUB_BLOB = Pattern.compile(
            "^https?://github\\.com/([^/]+)/([^/]+)/blob/([^/]+)/(.*)$",
            Pattern.CASE_INSENSITIVE);

    /** 重定向最大跳数（SSRF 防护：限制跟随链） */
    private static final int MAX_REDIRECTS = 5;

    @Resource
    private IntegrationCredentialsService credentials;

    @Resource
    private KnowledgeRuntimeSettings knowledgeRuntimeSettings;

    @Resource
    private UrlSafetyValidator urlSafetyValidator;

    private volatile HttpClient httpClient;
    private volatile int cachedTimeoutSeconds = -1;

    @Override
    public boolean supports(String inputType) {
        return "URL".equalsIgnoreCase(inputType);
    }

    @Override
    public String extract(String source) {
        try {
            String normalized = normalizeSourceUrl(source);
            // 协议白名单：所有抓取统一校验
            urlSafetyValidator.checkProtocol(normalized);

            // GitHub raw 可直连，避免 Jina 对 blob 页 403；直连做完整 SSRF 校验
            if (normalized.contains("raw.githubusercontent.com")) {
                return fetchDirect(normalized);
            }

            String rawBase = credentials.jinaBaseUrl();
            String baseUrl = rawBase.endsWith("/") ? rawBase : rawBase + "/";
            String encoded = URLEncoder.encode(normalized, StandardCharsets.UTF_8).replace("+", "%20");
            int timeout = effectiveTimeoutSeconds();
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + encoded))
                    .timeout(Duration.ofSeconds(timeout))
                    .header("User-Agent", "Ai-Backend-Knowledge/1.0")
                    .header("Accept", "text/plain, text/markdown, */*");
            if (StrUtil.isNotBlank(credentials.jinaApiKey())) {
                builder.header("Authorization", "Bearer " + credentials.jinaApiKey());
            }
            HttpRequest request = builder.GET().build();
            HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "Jina Reader 抓取失败：" + response.statusCode());
            }
            return response.body();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "Jina Reader 抓取异常：" + e.getMessage());
        }
    }

    /**
     * github.com/owner/repo/blob/branch/path → raw.githubusercontent.com/owner/repo/branch/path
     */
    static String normalizeSourceUrl(String source) {
        if (source == null || source.isBlank()) {
            return source;
        }
        String trimmed = source.trim();
        Matcher matcher = GITHUB_BLOB.matcher(trimmed);
        if (matcher.matches()) {
            return "https://raw.githubusercontent.com/"
                    + matcher.group(1) + "/"
                    + matcher.group(2) + "/"
                    + matcher.group(3) + "/"
                    + matcher.group(4);
        }
        return trimmed;
    }

    private String fetchDirect(String url) throws Exception {
        // 直连前完整 SSRF 校验（协议 + 主机 + 全部解析 IP）
        urlSafetyValidator.checkDirectFetch(url);
        int timeout = effectiveTimeoutSeconds();
        HttpClient client = httpClient().newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        String current = url;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            urlSafetyValidator.checkDirectFetch(current);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(current))
                    .timeout(Duration.ofSeconds(timeout))
                    .header("User-Agent", "Ai-Backend-Knowledge/1.0")
                    .header("Accept", "text/plain, text/markdown, */*")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (isRedirect(response.statusCode())) {
                String location = response.headers().firstValue("Location").orElse(null);
                if (StrUtil.isBlank(location)) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "重定向缺少 Location 头，URL=" + current);
                }
                current = resolveRedirect(current, location);
                continue;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR,
                        "直连抓取失败：" + response.statusCode() + "，URL=" + url);
            }
            return response.body();
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "重定向超过 " + MAX_REDIRECTS + " 次，已拦截，URL=" + url);
    }

    private boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private String resolveRedirect(String current, String location) throws Exception {
        URI base = URI.create(current);
        URI resolved = base.resolve(location);
        String next = resolved.toString();
        urlSafetyValidator.checkProtocol(next);
        return next;
    }

    private HttpClient httpClient() {
        int timeout = effectiveTimeoutSeconds();
        if (httpClient != null && cachedTimeoutSeconds == timeout) {
            return httpClient;
        }
        synchronized (this) {
            if (httpClient != null && cachedTimeoutSeconds == timeout) {
                return httpClient;
            }
            httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(timeout))
                    .build();
            cachedTimeoutSeconds = timeout;
            return httpClient;
        }
    }

    private int effectiveTimeoutSeconds() {
        return Math.max(5, knowledgeRuntimeSettings.jinaTimeoutSeconds());
    }
}
