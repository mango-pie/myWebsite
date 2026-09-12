package com.ai.service.knowledge.impl;

import com.ai.config.ConditionalOnModule;

import cn.hutool.core.util.StrUtil;
import com.ai.config.knowledge.KnowledgeDeepSeekProperties;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.service.IntegrationCredentialsService;
import com.ai.service.knowledge.support.KnowledgeMaterialCacheStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DeepSeek 官方联网能力：按用户勾选 URL 读取材料，并基于物化材料重构精读。
 */
@Slf4j
@ConditionalOnModule("knowledge")
@Service
public class KnowledgeDeepSeekReadingService {

    private static final int MIN_BODY_CHARS = 200;
    private static final int MATERIALIZE_MAX_TOKENS = 32768;
    /** 材料包压缩目标：reading 输出约输入 20%~30% */

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

    @Resource
    private KnowledgeMaterialCacheStore materialCacheStore;

    public MaterialBundle materialize(List<String> urls, String agentQuery) {
        if (urls == null || urls.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "urls 不能为空");
        }
        // 已缓存 URL 直接复用（30 天），只对未缓存 URL 发起模型调用
        List<MaterialSource> cached = new ArrayList<>();
        List<String> uncached = new ArrayList<>();
        for (String url : urls) {
            String json = materialCacheStore.get(url);
            if (json != null) {
                try {
                    MaterialSource source = objectMapper.readValue(json, MaterialSource.class);
                    cached.add(source);
                    continue;
                } catch (Exception e) {
                    log.debug("Material cache parse failed, url={}", url, e);
                }
            }
            uncached.add(url);
        }

        List<MaterialSource> used = new ArrayList<>(cached.stream()
                .filter(s -> "OK".equals(s.status()) && StrUtil.isNotBlank(s.bodyMarkdown()) && s.bodyMarkdown().trim().length() >= MIN_BODY_CHARS)
                .toList());
        List<MaterialSource> failed = new ArrayList<>(cached.stream()
                .filter(s -> !"OK".equals(s.status()) || StrUtil.isBlank(s.bodyMarkdown()) || s.bodyMarkdown().trim().length() < MIN_BODY_CHARS)
                .toList());

        if (!uncached.isEmpty()) {
            MaterialBundle fresh = materializeRemote(uncached, agentQuery);
            for (MaterialSource source : fresh.usedSources()) {
                materialCacheStore.put(source.url(), materialCacheStore.serialize(source));
            }
            used.addAll(fresh.usedSources());
            failed.addAll(fresh.failedSources());
        }

        // 保持与请求顺序一致：used 在前 failed 在后（每 URL 只出现一次）
        Set<String> seen = new HashSet<>();
        List<MaterialSource> mergedUsed = new ArrayList<>();
        List<MaterialSource> mergedFailed = new ArrayList<>();
        for (String url : urls) {
            if (!seen.add(url)) {
                continue;
            }
            for (MaterialSource s : used) {
                if (!seen.contains(s.url()) && url.equals(s.url())) {
                    mergedUsed.add(s);
                    seen.add(url);
                    break;
                }
            }
            for (MaterialSource s : failed) {
                if (!seen.contains(s.url()) && url.equals(s.url())) {
                    mergedFailed.add(s);
                    seen.add(url);
                    break;
                }
            }
        }
        for (MaterialSource s : used) {
            if (!seen.contains(s.url())) {
                mergedUsed.add(s);
            }
        }
        for (MaterialSource s : failed) {
            if (!seen.contains(s.url())) {
                mergedFailed.add(s);
            }
        }
        return new MaterialBundle(mergedUsed, mergedFailed);
    }

    private MaterialBundle materializeRemote(List<String> urls, String agentQuery) {
        if (urls.isEmpty()) {
            return new MaterialBundle(List.of(), List.of());
        }
        BusinessException lastError = null;
        for (String toolType : WEB_SEARCH_TOOL_TYPES) {
            try {
                JsonNode root = callMessages(
                        effectiveModel(properties.getReadingModel(), "deepseek-v4-flash"),
                        materializeSystemPrompt(),
                        materializeUserPrompt(urls, agentQuery),
                        MATERIALIZE_MAX_TOKENS,
                        0.1,
                        toolType);
                return parseMaterialBundle(root, urls);
            } catch (BusinessException e) {
                lastError = e;
            } catch (Exception e) {
                lastError = new BusinessException(ErrorCode.OPERATION_ERROR,
                        "DeepSeek 读取网页异常：" + e.getMessage());
            }
        }
        throw lastError == null
                ? new BusinessException(ErrorCode.OPERATION_ERROR, "DeepSeek 读取网页失败")
                : lastError;
    }

    public String distill(String materialText, String systemPrompt, Double temperature, Integer maxTokens) {
        if (StrUtil.isBlank(materialText)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "精读材料不能为空");
        }
        try {
            JsonNode root = callMessages(
                    effectiveModel(properties.getDistillModel(), "deepseek-v4-flash"),
                    systemPrompt,
                    "请只基于以下已物化材料重构一篇 Markdown 精读文章：\n\n" + materialText,
                    maxTokens == null || maxTokens <= 0 ? 4096 : maxTokens,
                    temperature,
                    null);
            String text = extractAllText(root.path("content"));
            if (StrUtil.isBlank(text)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "DeepSeek 精读重构响应为空");
            }
            return text.trim();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "DeepSeek 精读重构异常：" + e.getMessage());
        }
    }

    /**
     * 质量精修（pro 模型）：仅用于发布博客 / 入库 RAG 前的最后一版。
     * 失败自动降级：先试 flash 一次，仍失败则原样返回草稿，保证链路不空转。
     */
    public String polishForQuality(String draftMarkdown, String materialText, String agentQuery) {
        if (StrUtil.isBlank(draftMarkdown)) {
            return draftMarkdown;
        }
        String systemPrompt = """
                你是出版级技术精读编辑。对用户提供的精读草稿做最后一版质量精修：
                1. 修正事实 / 表述错误，但不得新增材料之外的内容。
                2. 保持 [srcN] 溯源标注不变。
                3. 保持 Markdown 结构，标题层级、代码块、配置项原样保留。
                4. 语言精炼、逻辑连贯；保留「参考来源」。
                5. 输出 Markdown，不要解释。
                学习目标：%s
                材料（供核对）：
                %s
                """.formatted(StrUtil.blankToDefault(agentQuery, "无明确目标"),
                materialText == null ? "" : materialText);
        String userPrompt = "精读草稿：\n" + draftMarkdown;
        try {
            JsonNode root = callMessages(
                    effectiveModel(properties.getPolishModel(), "deepseek-v4-pro"),
                    systemPrompt,
                    userPrompt,
                    8192,
                    0.2,
                    null);
            String text = extractAllText(root.path("content"));
            if (StrUtil.isNotBlank(text)) {
                return text.trim();
            }
        } catch (Exception e) {
            log.debug("Polish with pro model failed, try flash: {}", e.getMessage());
        }
        try {
            JsonNode root = callMessages(
                    effectiveModel(properties.getReadingModel(), "deepseek-v4-flash"),
                    systemPrompt,
                    userPrompt,
                    8192,
                    0.2,
                    null);
            String text = extractAllText(root.path("content"));
            if (StrUtil.isNotBlank(text)) {
                return text.trim();
            }
        } catch (Exception e) {
            log.warn("Polish fallback failed, keep draft: {}", e.getMessage());
        }
        return draftMarkdown;
    }

    private JsonNode callMessages(String model,
                                  String systemPrompt,
                                  String userPrompt,
                                  int maxTokens,
                                  Double temperature,
                                  String toolType) throws Exception {
        String apiKey = credentials.deepseekApiKey();
        if (StrUtil.isBlank(apiKey)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "未配置 DeepSeek 官方 API Key，请设置 knowledge.deepseek.api-key 或集成项 knowledge.deepseek.api_key");
        }
        String baseUrl = StrUtil.blankToDefault(credentials.deepseekBaseUrl(), properties.getBaseUrl())
                .replaceAll("/+$", "");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("system", systemPrompt);
        body.put("messages", List.of(Map.of("role", "user", "content", userPrompt)));
        if (temperature != null) {
            body.put("temperature", temperature);
        }
        if (StrUtil.isNotBlank(toolType)) {
            Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("type", toolType);
            tool.put("name", "web_search");
            tool.put("max_uses", 8);
            body.put("tools", List.of(tool));
            body.put("tool_choice", Map.of("type", "any"));
        }

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
            if (detail.length() > 500) {
                detail = detail.substring(0, 500);
            }
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "DeepSeek 调用失败：" + response.statusCode() + (detail.isBlank() ? "" : " " + detail));
        }
        return objectMapper.readTree(response.body());
    }

    private MaterialBundle parseMaterialBundle(JsonNode root, List<String> selectedUrls) {
        String text = extractAllText(root.path("content"));
        JsonNode parsed = parseJsonFromText(text);
        if (parsed == null) {
            if (selectedUrls.size() == 1 && StrUtil.isNotBlank(text) && text.length() >= MIN_BODY_CHARS) {
                String url = selectedUrls.get(0);
                return new MaterialBundle(List.of(new MaterialSource("src1", url, url, "OK", text.trim(), "", List.of(), List.of(), "", List.of())), List.of());
            }
            return allFailed(selectedUrls, "PARSE_ERROR", "DeepSeek 读取结果未返回可解析的材料 JSON");
        }

        JsonNode sourcesNode = parsed.isArray() ? parsed : parsed.path("sources");
        if (!sourcesNode.isArray()) {
            return allFailed(selectedUrls, "PARSE_ERROR", "DeepSeek 读取结果缺少 sources 数组");
        }

        List<MaterialSource> used = new ArrayList<>();
        List<MaterialSource> failed = new ArrayList<>();
        Set<String> covered = new HashSet<>();
        int autoRef = 0;
        for (JsonNode item : sourcesNode) {
            String returnedUrl = firstNonBlank(textOf(item, "url"), textOf(item, "sourceUrl"));
            String matchedUrl = matchSelectedUrl(returnedUrl, selectedUrls);
            if (StrUtil.isBlank(matchedUrl)) {
                continue;
            }
            covered.add(matchedUrl);
            String status = StrUtil.blankToDefault(textOf(item, "status"), "OK").trim().toUpperCase();
            String title = StrUtil.blankToDefault(textOf(item, "title"), matchedUrl);
            String body = firstNonBlank(textOf(item, "bodyMarkdown"), textOf(item, "markdown"), textOf(item, "body"));
            String reason = firstNonBlank(textOf(item, "reason"), textOf(item, "errorMessage"));
            String srcRef = StrUtil.blankToDefault(textOf(item, "sourceRef"), "src" + (++autoRef));
            List<String> keyPoints = stringList(item, "keyPoints");
            List<String> keyQuotes = stringList(item, "keyQuotes");
            String conclusion = textOf(item, "conclusion");
            List<String> conflicts = stringList(item, "conflicts");
            if (StrUtil.isBlank(body) && !keyPoints.isEmpty()) {
                body = composeCompressedBody(keyPoints, keyQuotes, conclusion);
            }
            if (!"OK".equals(status) || StrUtil.isBlank(body) || body.trim().length() < MIN_BODY_CHARS) {
                failed.add(new MaterialSource(srcRef, matchedUrl, title, "FAILED", "",
                        StrUtil.blankToDefault(reason, "AI 读取正文为空或过短"), keyPoints, keyQuotes, conclusion, conflicts));
            } else {
                used.add(new MaterialSource(srcRef, matchedUrl, title, "OK", body.trim(), "", keyPoints, keyQuotes, conclusion, conflicts));
            }
        }

        for (String url : selectedUrls) {
            if (!covered.contains(url)) {
                failed.add(new MaterialSource("src" + (++autoRef), url, url, "FAILED", "", "AI 读取结果未覆盖该 URL", List.of(), List.of(), "", List.of()));
            }
        }
        return new MaterialBundle(used, failed);
    }

    private String composeCompressedBody(List<String> keyPoints, List<String> keyQuotes, String conclusion) {
        StringBuilder sb = new StringBuilder();
        sb.append("### 要点\n");
        for (String p : keyPoints) {
            sb.append("- ").append(p).append('\n');
        }
        if (keyQuotes != null && !keyQuotes.isEmpty()) {
            sb.append("\n### 关键引文\n");
            for (String q : keyQuotes) {
                sb.append("> ").append(q).append('\n');
            }
        }
        if (StrUtil.isNotBlank(conclusion)) {
            sb.append("\n**结论：**").append(conclusion).append('\n');
        }
        return sb.toString();
    }

    private List<String> stringList(JsonNode node, String field) {
        List<String> list = new ArrayList<>();
        JsonNode arr = node.path(field);
        if (arr.isArray()) {
            for (JsonNode n : arr) {
                String v = n.asText("");
                if (StrUtil.isNotBlank(v)) {
                    list.add(v.trim());
                }
            }
        }
        return list;
    }

    private MaterialBundle allFailed(List<String> urls, String reasonCode, String reason) {
        List<MaterialSource> failed = new ArrayList<>();
        int i = 0;
        for (String url : urls) {
            failed.add(new MaterialSource("src" + (++i), url, url, reasonCode, "", reason, List.of(), List.of(), "", List.of()));
        }
        return new MaterialBundle(List.of(), failed);
    }

    private JsonNode parseJsonFromText(String text) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        String trimmed = text.trim();
        String json = sliceJson(trimmed, '{', '}');
        if (json == null) {
            json = sliceJson(trimmed, '[', ']');
        }
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String sliceJson(String text, char startChar, char endChar) {
        int start = text.indexOf(startChar);
        int end = text.lastIndexOf(endChar);
        if (start < 0 || end <= start) {
            return null;
        }
        return text.substring(start, end + 1);
    }

    private String materializeSystemPrompt() {
        return """
                你是网页材料压缩读取助手。必须只阅读用户给出的 URL，并把每个 URL 的正文压缩为「要点 + 关键引文」材料包。
                输出必须是严格 JSON，不要输出 Markdown 包裹，不要解释：
                {
                  "sources": [
                    {
                      "sourceRef": "src1",
                      "url": "原 URL",
                      "title": "页面标题",
                      "status": "OK 或 FAILED",
                      "bodyMarkdown": "紧凑要点 Markdown，包含：标题层级、每个核心要点的 1~3 句表述、代码块/配置项/命令/列表(原样保留，不可改写)、必要的关键引文(加引号原文短引)",
                      "keyPoints": ["要点1一句话", "要点2一句话"],
                      "keyQuotes": ["关键引文1"],
                      "conclusion": "全文结论 1~2 句",
                      "conflicts": ["与其它来源可能矛盾的点，无则空数组"],
                      "reason": "FAILED 时说明原因，OK 时为空"
                    }
                  ]
                }
                要求：
                1. sources 数量必须与用户 URL 数量一致，sourceRef 依次为 src1、src2、...
                2. bodyMarkdown 是压缩要点版，去掉冗长叙述与废话，输出长度控制在原文的 20%~30%；但代码块、配置项、命令必须原样保留。
                3. 读不到正文时不要编造，status 写 FAILED。
                4. 不要读取或引用用户未给出的 URL。
                """;
    }

    private String materializeUserPrompt(List<String> urls, String agentQuery) {
        StringBuilder sb = new StringBuilder();
        sb.append("学习目标：").append(StrUtil.blankToDefault(agentQuery, "无明确目标")).append("\n");
        sb.append("请阅读以下勾选 URL，并按系统要求返回 JSON：\n");
        for (int i = 0; i < urls.size(); i++) {
            sb.append(i + 1).append(". ").append(urls.get(i)).append('\n');
        }
        return sb.toString();
    }

    private String effectiveModel(String phaseModel, String fallback) {
        return StrUtil.blankToDefault(phaseModel, StrUtil.blankToDefault(properties.getModel(), fallback));
    }

    private String matchSelectedUrl(String returnedUrl, List<String> selectedUrls) {
        if (StrUtil.isBlank(returnedUrl)) {
            return "";
        }
        String cleanReturned = normalizeUrl(returnedUrl);
        for (String selected : selectedUrls) {
            if (returnedUrl.trim().equals(selected) || cleanReturned.equals(normalizeUrl(selected))) {
                return selected;
            }
        }
        for (String selected : selectedUrls) {
            String cleanSelected = normalizeUrl(selected);
            if (cleanReturned.contains(cleanSelected) || cleanSelected.contains(cleanReturned)) {
                return selected;
            }
        }
        return "";
    }

    private String normalizeUrl(String url) {
        String u = url.trim();
        int hash = u.indexOf('#');
        if (hash >= 0) {
            u = u.substring(0, hash);
        }
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
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

    public record MaterialBundle(List<MaterialSource> usedSources, List<MaterialSource> failedSources) {
    }

    public record MaterialSource(String srcRef, String url, String title, String status, String bodyMarkdown,
                                 String reason, List<String> keyPoints, List<String> keyQuotes,
                                 String conclusion, List<String> conflicts) {
    }
}
