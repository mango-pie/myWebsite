package com.ai.service.knowledge.support;

import cn.hutool.core.util.StrUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从文章正文抽取通用代码围栏与疑似配置行（不绑具体技术栈）。
 */
public final class SourceExcerptExtractor {

    private static final Pattern FENCE = Pattern.compile("(?s)```[^\\n]*\\n.*?```");
    private static final Pattern CONFIG_LINE = Pattern.compile(
            "(?m)^[ \\t]{0,4}[A-Za-z0-9_./-]{2,60}\\s*=\\s*\\S+.*$");

    private SourceExcerptExtractor() {
    }

    public static String extract(String rawText, int maxChars) {
        if (StrUtil.isBlank(rawText) || maxChars <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        Matcher fenceMatcher = FENCE.matcher(rawText);
        while (fenceMatcher.find()) {
            String block = fenceMatcher.group();
            if (sb.length() + block.length() + 2 > maxChars) {
                break;
            }
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append(block.trim());
        }
        if (sb.length() >= maxChars * 0.6) {
            return sb.toString();
        }
        List<String> configLines = new ArrayList<>();
        Matcher configMatcher = CONFIG_LINE.matcher(rawText);
        while (configMatcher.find() && configLines.size() < 40) {
            String line = configMatcher.group().trim();
            if (line.length() > 200) {
                continue;
            }
            configLines.add(line);
        }
        if (!configLines.isEmpty()) {
            String cfg = String.join("\n", configLines);
            if (sb.length() + cfg.length() + 20 <= maxChars) {
                if (!sb.isEmpty()) {
                    sb.append("\n\n");
                }
                sb.append("```\n").append(cfg).append("\n```");
            }
        }
        return sb.length() > maxChars ? sb.substring(0, maxChars) : sb.toString();
    }

    public static boolean hasUsableExcerpt(String excerpt) {
        return StrUtil.isNotBlank(excerpt) && excerpt.trim().length() >= 20;
    }
}
