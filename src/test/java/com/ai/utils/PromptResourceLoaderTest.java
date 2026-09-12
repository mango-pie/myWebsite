package com.ai.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PromptResourceLoaderTest {

    @Test
    void loadPrompt_readsUtf8WithoutMojibake() {
        String prompt = PromptResourceLoader.load("prompt/codegen-html-system-prompt.txt");
        assertFalse(prompt.isBlank());
        // 正确的 UTF-8 读取应包含中文
        assertTrue(prompt.contains("前端"));
        // 若出现该乱码片段说明发生了编码错误
        assertFalse(prompt.contains("浣犳"));
    }
}
