package com.ai.service.knowledge.support;

import cn.hutool.core.util.StrUtil;
import com.ai.constant.AiUsageSceneConstant;
import com.ai.ops.AiUsageCallContext;
import com.ai.service.knowledge.KnowledgeAiModelService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 对齐度自检：flash 判断精读稿与学习目标的贴合度，低分提示换材料 / 重蒸。
 * 合蒸链路与 URL/文件单篇蒸馏共用，避免两套实现漂移。
 */
@Slf4j
@Component
public class KnowledgeAlignmentChecker {

    private static final int MAX_MARKDOWN_CHARS = 12_000;
    private static final int MAX_NOTE_CHARS = 200;

    @Resource
    private KnowledgeAiModelService aiModelService;

    @Resource
    private ObjectMapper objectMapper;

    public record AlignmentResult(Double score, String note) {
    }

    /**
     * @param markdown 精读稿
     * @param goal     学习目标（空则跳过，不烧模型）
     * @param userId   当前用户
     * @param sourceId 溯源 ID（计入用量日志 conversationId）
     * @return 解析失败或目标为空时返回 null
     */
    public AlignmentResult check(String markdown, String goal, Long userId, Long sourceId) {
        if (StrUtil.isBlank(markdown) || StrUtil.isBlank(goal) || userId == null) {
            return null;
        }
        String content = markdown.length() > MAX_MARKDOWN_CHARS
                ? markdown.substring(0, MAX_MARKDOWN_CHARS)
                : markdown;
        String prompt = """
                你是学习目标对齐度评审。仅输出 JSON：{"score":0到100整数,"note":"一句话说明"}
                规则：评估精读稿内容与学习目标的贴合程度；材料离题、答非所问、覆盖严重不足时给低分。
                学习目标：%s
                精读稿：
                %s
                """.formatted(goal, content);
        AiUsageCallContext.set(userId, AiUsageSceneConstant.ALIGNMENT, sourceId, "alignment-check");
        String raw;
        try {
            raw = aiModelService.chat(prompt);
        } finally {
            AiUsageCallContext.clear();
        }
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        try {
            String json = raw.trim();
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return null;
            }
            JsonNode node = objectMapper.readTree(json.substring(start, end + 1));
            int score = Math.max(0, Math.min(100, node.path("score").asInt(50)));
            String note = node.path("note").asText(null);
            if (note != null && note.length() > MAX_NOTE_CHARS) {
                note = note.substring(0, MAX_NOTE_CHARS);
            }
            return new AlignmentResult(score / 100.0, note);
        } catch (Exception e) {
            log.warn("alignment check parse failed", e);
            return null;
        }
    }
}
