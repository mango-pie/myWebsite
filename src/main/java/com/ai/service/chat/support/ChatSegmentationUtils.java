package com.ai.service.chat.support;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 智能分段纯逻辑（移植自 astrbot_plugin_smart_segmentation/segmentation.py）。
 */
public final class ChatSegmentationUtils {

    private static final Map<String, String> STYLE_GUIDES = Map.of(
            "natural", "像和朋友微信聊天一样自然地分条发送。有的消息短有的长，节奏随意。",
            "conservative", "偏沉稳的发消息风格，一条消息说比较完整的内容，不会频繁发短消息。",
            "active", "活泼的发消息风格，喜欢发短消息连击，反应词和正文分开发。"
    );

    private static final List<BracketPair> BRACKET_PAIRS = List.of(
            new BracketPair('（', '）'),
            new BracketPair('(', ')'),
            new BracketPair('【', '】'),
            new BracketPair('[', ']')
    );

    /** AstrBot 内置分段同款标点切分，LLM 不可用时的兜底 */
    private static final Pattern PUNCTUATION_SPLIT_PATTERN = Pattern.compile(".*?[。？！~…]+|.+$");

    private ChatSegmentationUtils() {
    }

    public static String buildSegmentationPrompt(String text, String style, int maxSegments) {
        String styleGuide = STYLE_GUIDES.getOrDefault(style, STYLE_GUIDES.get("natural"));
        return """
                你正在模拟一个人用手机聊天。下面是 ta 想说的内容，请把它分成几条消息，就像真人会怎么一条一条发出来那样。

                %s

                规则：
                - 不要改写原意，不要补充新信息
                - 去掉每条消息末尾的句号「。」
                - 保留感叹号、问号、省略号、波浪号等有情绪的标点
                - 不要每个逗号都拆开，相关的内容放在一条里
                - 消息长短可以不均匀
                - 括号（中文「（）」「【】」或英文「()」「[]」）内的内容（动作、神态、旁白等描述）必须作为独立的一条消息单独发送，不要和括号外的正文合在同一条
                - 括号内的内容本身不能再拆开，需保持完整
                - 如果整段内容就是被括号包裹的动作/神态描述，直接整段返回不再切分
                - 最多分成 %d 条
                - 如果不适合切分，就返回只包含原文的一项数组

                原文：%s

                只返回 JSON 数组，如 ["消息1", "消息2"]""".formatted(styleGuide, maxSegments, text);
    }

    public static List<String> parseSegmentsFromModelOutput(String rawText, String fallbackText, int maxSegments) {
        try {
            String jsonText = extractJsonArrayText(rawText);
            JSONArray array = JSONUtil.parseArray(jsonText);
            List<String> normalized = normalizeSegments(array, maxSegments);
            List<String> balanced = mergeSegmentsBalancingBrackets(normalized);
            return splitSegmentsAtBracketBoundaries(balanced, maxSegments);
        } catch (Exception ignore) {
            String fallback = fallbackText == null ? "" : fallbackText.trim();
            return fallback.isEmpty() ? List.of() : List.of(fallback);
        }
    }

    public static long calculateSendDelayMs(String segment, double delayBase, double delayPerChar, double delayMax) {
        double normalizedDelay = delayBase + segment.length() * delayPerChar;
        normalizedDelay += ThreadLocalRandom.current().nextDouble(0.0, 0.15);
        double clamped = Math.max(0.0, Math.min(delayMax, normalizedDelay));
        return Math.round(clamped * 1000);
    }

    public static List<Long> buildDelaySchedule(List<String> segments, double delayBase, double delayPerChar, double delayMax) {
        List<Long> delays = new ArrayList<>(segments.size());
        for (int i = 0; i < segments.size(); i++) {
            if (i == 0) {
                delays.add(0L);
            } else {
                delays.add(calculateSendDelayMs(segments.get(i), delayBase, delayPerChar, delayMax));
            }
        }
        return delays;
    }

    /**
     * 标点规则兜底分段（AstrBot segmented_reply 同款），LLM 失败或未拆分时使用。
     */
    public static List<String> splitByPunctuationFallback(String text, int maxSegments) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String trimmed = text.trim();
        Matcher matcher = PUNCTUATION_SPLIT_PATTERN.matcher(trimmed);
        List<String> parts = new ArrayList<>();
        while (matcher.find()) {
            String part = matcher.group().trim();
            if (!part.isEmpty()) {
                parts.add(part);
            }
        }
        if (parts.size() <= 1) {
            return List.of(trimmed);
        }
        List<String> balanced = mergeSegmentsBalancingBrackets(parts);
        return splitSegmentsAtBracketBoundaries(balanced, maxSegments);
    }

    public static boolean hasMultipleSegments(List<String> segments) {
        return segments != null && segments.size() > 1;
    }

    static String extractJsonArrayText(String rawText) {
        String resultText = rawText == null ? "" : rawText.trim();
        if (resultText.contains("```json")) {
            return resultText.split("```json", 2)[1].split("```", 2)[0].trim();
        }
        if (resultText.contains("```")) {
            return resultText.split("```", 2)[1].split("```", 2)[0].trim();
        }
        int start = resultText.indexOf('[');
        int end = resultText.lastIndexOf(']');
        if (start != -1 && end != -1 && start < end) {
            return resultText.substring(start, end + 1);
        }
        return resultText;
    }

    static List<String> normalizeSegments(JSONArray segments, int maxSegments) {
        if (segments == null || segments.isEmpty()) {
            throw new IllegalArgumentException("empty segments");
        }
        List<String> normalized = new ArrayList<>();
        for (Object segment : segments) {
            String value = String.valueOf(segment).trim();
            if (!value.isEmpty()) {
                normalized.add(value);
            }
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("empty segments");
        }
        if (maxSegments > 0 && normalized.size() > maxSegments) {
            List<String> head = new ArrayList<>(normalized.subList(0, maxSegments - 1));
            head.add(String.join("", normalized.subList(maxSegments - 1, normalized.size())));
            return head;
        }
        return normalized;
    }

    static List<String> mergeSegmentsBalancingBrackets(List<String> segments) {
        if (segments == null || segments.isEmpty()) {
            return List.of();
        }
        List<String> merged = new ArrayList<>();
        String buffer = "";
        for (String segment : segments) {
            buffer = buffer.isEmpty() ? segment : buffer + segment;
            if (!hasUnbalancedBrackets(buffer)) {
                merged.add(buffer);
                buffer = "";
            }
        }
        if (!buffer.isEmpty()) {
            merged.add(buffer);
        }
        return merged;
    }

    static List<String> splitSegmentsAtBracketBoundaries(List<String> segments, int maxSegments) {
        if (segments == null || segments.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String segment : segments) {
            for (String part : splitTextAtBrackets(segment)) {
                String stripped = part.trim();
                if (!stripped.isEmpty()) {
                    result.add(stripped);
                }
            }
        }
        if (result.isEmpty()) {
            return result;
        }
        if (maxSegments > 0 && result.size() > maxSegments) {
            List<String> head = new ArrayList<>(result.subList(0, maxSegments - 1));
            head.add(String.join("", result.subList(maxSegments - 1, result.size())));
            return head;
        }
        return result;
    }

    static List<String> splitTextAtBrackets(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        List<String> parts = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        int index = 0;
        while (index < text.length()) {
            char ch = text.charAt(index);
            BracketPair matched = null;
            for (BracketPair pair : BRACKET_PAIRS) {
                if (ch == pair.open()) {
                    matched = pair;
                    break;
                }
            }
            if (matched == null) {
                buffer.append(ch);
                index++;
                continue;
            }
            if (!buffer.isEmpty()) {
                parts.add(buffer.toString());
                buffer.setLength(0);
            }
            int depth = 1;
            int scanIndex = index + 1;
            while (scanIndex < text.length() && depth > 0) {
                char scanChar = text.charAt(scanIndex);
                if (scanChar == matched.open()) {
                    depth++;
                } else if (scanChar == matched.close()) {
                    depth--;
                }
                scanIndex++;
            }
            if (depth != 0) {
                buffer.append(text.substring(index));
                index = text.length();
                break;
            }
            parts.add(text.substring(index, scanIndex));
            index = scanIndex;
        }
        if (!buffer.isEmpty()) {
            parts.add(buffer.toString());
        }
        return parts;
    }

    static boolean hasUnbalancedBrackets(String text) {
        for (BracketPair pair : BRACKET_PAIRS) {
            int openCount = 0;
            int closeCount = 0;
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (ch == pair.open()) {
                    openCount++;
                } else if (ch == pair.close()) {
                    closeCount++;
                }
            }
            if (openCount != closeCount) {
                return true;
            }
        }
        return false;
    }

    private record BracketPair(char open, char close) {
    }
}
