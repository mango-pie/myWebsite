package com.ai.service.knowledge.support;

import cn.hutool.core.util.StrUtil;
import com.ai.utils.PromptResourceLoader;

/**
 * 精读格式契约：classpath {@code skills/knowledge-reading/SKILL.md}。
 */
public final class ReadingSkillLoader {

    public static final String RESOURCE = "skills/knowledge-reading/SKILL.md";

    private static volatile String cachedRaw;
    private static volatile String cachedBody;
    private static volatile String cachedName;
    private static volatile String cachedDescription;

    private ReadingSkillLoader() {
    }

    public static String raw() {
        if (cachedRaw == null) {
            synchronized (ReadingSkillLoader.class) {
                if (cachedRaw == null) {
                    parse(PromptResourceLoader.load(RESOURCE));
                }
            }
        }
        return cachedRaw;
    }

    public static String body() {
        raw();
        return cachedBody;
    }

    public static String name() {
        raw();
        return cachedName;
    }

    public static String description() {
        raw();
        return cachedDescription;
    }

    /**
     * 组装第二步系统提示：Skill 正文 + 学习目标 + 可选大纲/附加指令。
     */
    public static String buildSystemPrompt(String agentQuery, String outline, String extraInstructions) {
        StringBuilder sb = new StringBuilder(body().trim());
        sb.append("\n\n学习目标：")
                .append(StrUtil.blankToDefault(StrUtil.trim(agentQuery), "无明确目标"));
        if (StrUtil.isNotBlank(outline)) {
            sb.append("\n合蒸大纲（章节须对齐此骨架）：\n").append(outline.trim());
        }
        if (StrUtil.isNotBlank(extraInstructions)) {
            sb.append("\n附加指令（不得覆盖 Skill 的硬性约束与标题结构）：\n")
                    .append(extraInstructions.trim());
        }
        return sb.toString();
    }

    static void resetCacheForTest() {
        cachedRaw = null;
        cachedBody = null;
        cachedName = null;
        cachedDescription = null;
    }

    private static void parse(String raw) {
        cachedRaw = raw;
        cachedName = "knowledge-reading";
        cachedDescription = "";
        String text = raw;
        if (text.startsWith("---")) {
            int end = text.indexOf("\n---", 3);
            if (end > 0) {
                String front = text.substring(3, end).trim();
                int bodyStart = end + "\n---".length();
                while (bodyStart < text.length() && (text.charAt(bodyStart) == '\r' || text.charAt(bodyStart) == '\n')) {
                    bodyStart++;
                }
                cachedBody = text.substring(bodyStart).trim();
                for (String line : front.split("\n")) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("name:")) {
                        cachedName = trimmed.substring("name:".length()).trim();
                    } else if (trimmed.startsWith("description:")) {
                        cachedDescription = trimmed.substring("description:".length()).trim();
                    }
                }
                return;
            }
        }
        cachedBody = text.trim();
    }
}
