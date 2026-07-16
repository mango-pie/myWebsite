package com.ai.service.knowledge.impl;

import cn.hutool.core.util.StrUtil;
import com.ai.config.knowledge.KnowledgeTavilyProperties;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.model.dto.knowledge.SearchOptions;
import com.ai.model.vo.knowledge.SearchResult;
import com.ai.service.IntegrationCredentialsService;
import com.ai.service.knowledge.KnowledgeSearchStrategy;
import com.ai.service.knowledge.support.SearchCandidateRiskHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TavilyKnowledgeSearchStrategy implements KnowledgeSearchStrategy {

    private static final String ARTICLE_QUERY_SUFFIX =
            "优质技术文章 OR 官方文档 OR tutorial guide blog documentation -video -youtube -bilibili -playlist";

    private static final int INCLUDE_FALLBACK_MIN = 2;

    @Resource
    private KnowledgeTavilyProperties properties;

    @Resource
    private IntegrationCredentialsService credentials;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public String name() {
        return "tavily";
    }

    @Override
    public List<SearchResult> search(String query, SearchOptions options) {
        if (StrUtil.isBlank(query)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "搜索目标不能为空");
        }
        String apiKey = credentials.tavilyApiKey();
        if (StrUtil.isBlank(apiKey)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "未配置 Tavily API Key，请设置 knowledge.tavily.api-key 或站点集成项 knowledge.tavily.api_key");
        }

        int maxResults = resolveMaxResults(options);
        String baseUrl = StrUtil.blankToDefault(credentials.tavilyBaseUrl(), properties.getBaseUrl()).replaceAll("/+$", "");
        String searchQuery = buildQuery(query, options);
        List<String> exclude = cleanDomains(properties.getExcludeDomains());
        List<String> include = cleanDomains(properties.getIncludeDomains());

        try {
            List<SearchResult> results = executeSearch(baseUrl, apiKey, searchQuery, maxResults, exclude, include);
            if (results.size() < INCLUDE_FALLBACK_MIN && !include.isEmpty()) {
                results = executeSearch(baseUrl, apiKey, searchQuery, maxResults, exclude, List.of());
            }
            return results;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "Tavily 搜索异常：" + e.getMessage());
        }
    }

    private List<SearchResult> executeSearch(String baseUrl,
                                             String apiKey,
                                             String searchQuery,
                                             int maxResults,
                                             List<String> exclude,
                                             List<String> include) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("api_key", apiKey);
        body.put("query", searchQuery);
        body.put("max_results", maxResults);
        body.put("search_depth", "basic");
        body.put("include_answer", false);
        if (!exclude.isEmpty()) {
            body.put("exclude_domains", exclude);
        }
        if (!include.isEmpty()) {
            body.put("include_domains", include);
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(30, properties.getTimeoutSeconds())))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/search"))
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "Tavily 搜索失败：" + response.statusCode());
        }
        return mapResults(objectMapper.readTree(response.body()), exclude);
    }

    private int resolveMaxResults(SearchOptions options) {
        int configured = properties.getMaxResults() <= 0 ? 5 : properties.getMaxResults();
        if (options != null && options.getMaxResults() != null && options.getMaxResults() > 0) {
            configured = options.getMaxResults();
        }
        return Math.min(20, Math.max(1, configured));
    }

    private String buildQuery(String query, SearchOptions options) {
        StringBuilder sb = new StringBuilder(query.trim());
        if (options != null && StrUtil.isNotBlank(options.getPreference())) {
            sb.append(' ').append(options.getPreference().trim());
        }
        sb.append(' ').append(ARTICLE_QUERY_SUFFIX);
        return sb.toString();
    }

    private List<SearchResult> mapResults(JsonNode root, List<String> excludeDomains) {
        List<SearchResult> list = new ArrayList<>();
        JsonNode results = root == null ? null : root.path("results");
        if (results == null || !results.isArray()) {
            return list;
        }
        for (JsonNode item : results) {
            String url = text(item, "url");
            if (StrUtil.isBlank(url)) {
                continue;
            }
            // 双保险：排除视频域名
            if (SearchCandidateRiskHelper.matchesDomainList(url, excludeDomains)) {
                continue;
            }
            String title = StrUtil.blankToDefault(text(item, "title"), url);
            String summary = text(item, "content");
            Double score = item.has("score") && item.get("score").isNumber()
                    ? item.get("score").asDouble() : null;
            List<String> riskFlags = SearchCandidateRiskHelper.riskFlags(url);
            list.add(SearchResult.builder()
                    .title(title)
                    .url(url)
                    .summary(summary)
                    .source("tavily")
                    .score(score)
                    .recommendReason(buildReason(summary, score, riskFlags))
                    .riskFlags(riskFlags)
                    .build());
        }
        return list;
    }

    private String buildReason(String summary, Double score, List<String> riskFlags) {
        StringBuilder sb = new StringBuilder("偏文章/文档候选");
        if (score != null) {
            sb.append(String.format("；相关度 %.2f", score));
        }
        if (riskFlags != null && !riskFlags.isEmpty()) {
            sb.append("；风险：").append(String.join(",", riskFlags));
        }
        if (StrUtil.isNotBlank(summary)) {
            sb.append("；");
            String snippet = summary.length() <= 80 ? summary : summary.substring(0, 80) + "…";
            sb.append(snippet);
        }
        return sb.toString();
    }

    private List<String> cleanDomains(List<String> domains) {
        if (domains == null || domains.isEmpty()) {
            return List.of();
        }
        List<String> cleaned = new ArrayList<>();
        for (String d : domains) {
            if (StrUtil.isNotBlank(d)) {
                cleaned.add(d.trim().toLowerCase());
            }
        }
        return cleaned;
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return "";
        }
        return node.get(field).asText("");
    }
}
