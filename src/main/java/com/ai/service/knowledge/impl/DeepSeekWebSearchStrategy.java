package com.ai.service.knowledge.impl;

import com.ai.config.ConditionalOnModule;

import cn.hutool.core.util.StrUtil;
import com.ai.config.knowledge.KnowledgeDeepSeekProperties;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DeepSeek 官方 Anthropic 兼容端 + web_search：对齐网页版联网找页，产出候选 URL 列表。
 */
@ConditionalOnModule("knowledge")
@Service
public class DeepSeekWebSearchStrategy implements KnowledgeSearchStrategy {

    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+",
            Pattern.CASE_INSENSITIVE);

    private static final List<String> VIDEO_DOMAINS = List.of(
            "youtube.com", "youtu.be", "bilibili.com", "b23.tv", "vimeo.com",
            "tiktok.com", "douyin.com", "ixigua.com", "youku.com", "iqiyi.com"
    );

    private static final String[] WEB_SEARCH_TOOL_TYPES = {
            "web_search_20260209",
            "web_search_20250305"
    };

    @Resource
    private KnowledgeDeepSeekProperties properties;

    @Resource
    private IntegrationCredentialsService credentials;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public String name() {
        return "deepseek";
    }

    @Override
    public List<SearchResult> search(String query, SearchOptions options) {
        if (StrUtil.isBlank(query)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "搜索目标不能为空");
        }
        String apiKey = credentials.deepseekApiKey();
        if (StrUtil.isBlank(apiKey)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "未配置 DeepSeek 官方 API Key，请设置 knowledge.deepseek.api-key 或集成项 knowledge.deepseek.api_key（与 DashScope knowledge.ai 不是同一个）");
        }

        String baseUrl = StrUtil.blankToDefault(credentials.deepseekBaseUrl(), properties.getBaseUrl())
                .replaceAll("/+$", "");
        String model = StrUtil.blankToDefault(properties.getSearchModel(),
                StrUtil.blankToDefault(properties.getModel(), "deepseek-v4-flash"));
        int maxResults = properties.getMaxResults() <= 0 ? 8 : Math.min(12, properties.getMaxResults());
        if (options != null && options.getMaxResults() != null && options.getMaxResults() > 0) {
            maxResults = Math.min(12, options.getMaxResults());
        }

        BusinessException lastError = null;
        for (String toolType : WEB_SEARCH_TOOL_TYPES) {
            try {
                JsonNode root = callMessages(baseUrl, apiKey, model, toolType, query, options, maxResults);
                List<SearchResult> results = parseResults(root, maxResults);
                if (!results.isEmpty()) {
                    return results;
                }
            } catch (BusinessException e) {
                lastError = e;
            } catch (Exception e) {
                lastError = new BusinessException(ErrorCode.OPERATION_ERROR,
                        "DeepSeek 联网搜索异常：" + e.getMessage());
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "DeepSeek 联网搜索未返回可用文章 URL");
    }

    private JsonNode callMessages(String baseUrl,
                                  String apiKey,
                                  String model,
                                  String toolType,
                                  String query,
                                  SearchOptions options,
                                  int maxResults) throws Exception {
        String preference = options == null ? null : options.getPreference();
        String system = """
                你是技术资料检索助手，使用联网搜索为用户找「可打开阅读的技术文章或官方文档页面」。
                硬性要求：
                1. 优先官方文档章节页、高质量技术博客长文。
                2. 不要视频站、播放列表、纯课程售卖页、需要登录墙才能看到正文的页。
                3. 最终必须给出 %d 条左右候选，每条含 title、url、reason（一句话推荐理由）。
                4. 在回复末尾额外给出 JSON 数组，格式严格为：
                [{"title":"...","url":"https://...","reason":"..."}]
                只输出可访问的 http(s) 链接。
                """.formatted(maxResults);
        String user = """
                学习目标：%s
                偏好：%s
                请联网搜索并返回候选文章列表。
                """.formatted(query.trim(), StrUtil.blankToDefault(preference, "无"));

        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", toolType);
        tool.put("name", "web_search");
        tool.put("max_uses", 5);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", 4096);
        body.put("system", system);
        body.put("tools", List.of(tool));
        body.put("tool_choice", Map.of("type", "any"));
        body.put("messages", List.of(Map.of(
                "role", "user",
                "content", user
        )));

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(30, properties.getTimeoutSeconds())))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/messages"))
                .timeout(Duration.ofSeconds(Math.max(30, properties.getTimeoutSeconds())))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String detail = response.body() == null ? "" : response.body();
            if (detail.length() > 300) {
                detail = detail.substring(0, 300);
            }
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "DeepSeek 联网搜索失败：" + response.statusCode() + (detail.isBlank() ? "" : " " + detail));
        }
        return objectMapper.readTree(response.body());
    }

    private List<SearchResult> parseResults(JsonNode root, int maxResults) {
        Map<String, SearchResult> byUrl = new LinkedHashMap<>();

        // 1) JSON 数组（模型约定）
        collectFromJsonText(root, byUrl);
        // 2) web_search_tool_result / citations
        collectFromContentBlocks(root.path("content"), byUrl);
        // 3) 正文里的裸 URL
        collectBareUrls(root, byUrl);

        List<SearchResult> list = new ArrayList<>();
        for (SearchResult item : byUrl.values()) {
            if (SearchCandidateRiskHelper.matchesDomainList(item.getUrl(), VIDEO_DOMAINS)) {
                continue;
            }
            list.add(item);
            if (list.size() >= maxResults) {
                break;
            }
        }
        return list;
    }

    private void collectFromJsonText(JsonNode root, Map<String, SearchResult> byUrl) {
        String text = extractAllText(root.path("content"));
        if (StrUtil.isBlank(text)) {
            return;
        }
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return;
        }
        String json = text.substring(start, end + 1);
        try {
            JsonNode arr = objectMapper.readTree(json);
            if (!arr.isArray()) {
                return;
            }
            for (JsonNode item : arr) {
                String url = textOf(item, "url");
                if (StrUtil.isBlank(url) || !url.startsWith("http")) {
                    continue;
                }
                putCandidate(byUrl, url, textOf(item, "title"), textOf(item, "reason"), null);
            }
        } catch (Exception ignored) {
            // ignore malformed trailing json
        }
    }

    private void collectFromContentBlocks(JsonNode content, Map<String, SearchResult> byUrl) {
        if (content == null || !content.isArray()) {
            return;
        }
        for (JsonNode block : content) {
            String type = textOf(block, "type");
            if ("web_search_tool_result".equals(type)) {
                JsonNode inner = block.path("content");
                if (inner.isArray()) {
                    for (JsonNode hit : inner) {
                        String url = firstNonBlank(textOf(hit, "url"), textOf(hit, "link"));
                        String title = firstNonBlank(textOf(hit, "title"), textOf(hit, "name"));
                        String summary = firstNonBlank(textOf(hit, "snippet"), textOf(hit, "content"), textOf(hit, "description"));
                        if (StrUtil.isNotBlank(url)) {
                            putCandidate(byUrl, url, title, summary, null);
                        }
                        // nested
                        collectUrlsRecursive(hit, byUrl);
                    }
                } else if (inner.isTextual()) {
                    scrapeUrlsFromString(inner.asText(), byUrl);
                }
            }
            if ("text".equals(type)) {
                JsonNode citations = block.path("citations");
                if (citations.isArray()) {
                    for (JsonNode c : citations) {
                        String url = firstNonBlank(textOf(c, "url"), textOf(c, "source"));
                        String title = textOf(c, "title");
                        if (StrUtil.isNotBlank(url)) {
                            putCandidate(byUrl, url, title, textOf(c, "cited_text"), null);
                        }
                    }
                }
            }
            collectUrlsRecursive(block, byUrl);
        }
    }

    private void collectBareUrls(JsonNode root, Map<String, SearchResult> byUrl) {
        scrapeUrlsFromString(extractAllText(root.path("content")), byUrl);
    }

    private void collectUrlsRecursive(JsonNode node, Map<String, SearchResult> byUrl) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            String url = textOf(node, "url");
            if (StrUtil.isNotBlank(url) && url.startsWith("http")) {
                putCandidate(byUrl, url, textOf(node, "title"),
                        firstNonBlank(textOf(node, "snippet"), textOf(node, "reason")), null);
            }
            node.fields().forEachRemaining(e -> collectUrlsRecursive(e.getValue(), byUrl));
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                collectUrlsRecursive(child, byUrl);
            }
        }
    }

    private void scrapeUrlsFromString(String text, Map<String, SearchResult> byUrl) {
        if (StrUtil.isBlank(text)) {
            return;
        }
        Matcher matcher = URL_PATTERN.matcher(text);
        while (matcher.find()) {
            String url = trimTrailingPunct(matcher.group());
            if (url.startsWith("http")) {
                putCandidate(byUrl, url, null, null, null);
            }
        }
    }

    private void putCandidate(Map<String, SearchResult> byUrl,
                              String url,
                              String title,
                              String reason,
                              Double score) {
        String cleaned = trimTrailingPunct(url);
        if (StrUtil.isBlank(cleaned) || !cleaned.startsWith("http")) {
            return;
        }
        SearchResult existing = byUrl.get(cleaned);
        if (existing != null) {
            if (StrUtil.isBlank(existing.getTitle()) && StrUtil.isNotBlank(title)) {
                existing.setTitle(title);
            }
            if (StrUtil.isBlank(existing.getRecommendReason()) && StrUtil.isNotBlank(reason)) {
                existing.setRecommendReason(reason);
                existing.setSummary(reason);
            }
            return;
        }
        String displayTitle = StrUtil.blankToDefault(title, cleaned);
        String reasonText = StrUtil.blankToDefault(reason, "DeepSeek 联网候选");
        byUrl.put(cleaned, SearchResult.builder()
                .title(displayTitle)
                .url(cleaned)
                .summary(reasonText)
                .source("deepseek")
                .score(score)
                .recommendReason(reasonText)
                .riskFlags(SearchCandidateRiskHelper.riskFlags(cleaned))
                .build());
    }

    private String extractAllText(JsonNode content) {
        if (content == null || content.isNull()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (content.isArray()) {
            for (JsonNode block : content) {
                if ("text".equals(textOf(block, "type"))) {
                    sb.append(textOf(block, "text")).append('\n');
                } else if (block.has("text")) {
                    sb.append(textOf(block, "text")).append('\n');
                }
            }
        } else if (content.isTextual()) {
            sb.append(content.asText());
        }
        return sb.toString();
    }

    private String textOf(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return "";
        }
        return node.get(field).asText("");
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String v : values) {
            if (StrUtil.isNotBlank(v)) {
                return v.trim();
            }
        }
        return "";
    }

    private String trimTrailingPunct(String url) {
        String u = url.trim();
        while (!u.isEmpty()) {
            char c = u.charAt(u.length() - 1);
            if (c == '.' || c == ',' || c == ')' || c == ']' || c == '"' || c == '\'' || c == '>') {
                u = u.substring(0, u.length() - 1);
            } else {
                break;
            }
        }
        return u;
    }
}
