package com.ai.service.knowledge.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadingSkillLoaderTest {

    @Test
    void loadsSkillWithoutFrontmatter() {
        ReadingSkillLoader.resetCacheForTest();
        String body = ReadingSkillLoader.body();
        assertFalse(body.startsWith("---"));
        assertTrue(body.contains("## 核心思想"));
        assertTrue(body.contains("## 关键知识点"));
        assertTrue(body.contains("## 代码/配置精髓"));
        assertTrue(body.contains("## 延伸问题"));
        assertTrue(body.contains("## 参考来源"));
        assertTrue("knowledge-reading".equals(ReadingSkillLoader.name()));
    }

    @Test
    void buildSystemPromptAppendsGoalAndExtra() {
        ReadingSkillLoader.resetCacheForTest();
        String prompt = ReadingSkillLoader.buildSystemPrompt("Redis 持久化", null, "语气更短");
        assertTrue(prompt.contains("学习目标：Redis 持久化"));
        assertTrue(prompt.contains("附加指令"));
        assertTrue(prompt.contains("语气更短"));
        assertTrue(prompt.contains("# 精读 Markdown"));
    }
}
