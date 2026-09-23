package com.ai.service.chat.support;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatSegmentationUtilsTest {

    @Test
    void parseSegmentsFromModelOutput_parsesJsonArray() {
        String raw = """
                ```json
                ["你好呀", "今天怎么样"]
                ```
                """;
        List<String> segments = ChatSegmentationUtils.parseSegmentsFromModelOutput(raw, "fallback", 8);
        assertEquals(List.of("你好呀", "今天怎么样"), segments);
    }

    @Test
    void parseSegmentsFromModelOutput_splitsBracketActionSegments() {
        String raw = "[\"正文开始\", \"（轻轻点头）\", \"继续说话\"]";
        List<String> segments = ChatSegmentationUtils.parseSegmentsFromModelOutput(raw, "fallback", 8);
        assertEquals(3, segments.size());
        assertEquals("正文开始", segments.get(0));
        assertEquals("（轻轻点头）", segments.get(1));
        assertEquals("继续说话", segments.get(2));
    }

    @Test
    void parseSegmentsFromModelOutput_fallsBackOnInvalidJson() {
        List<String> segments = ChatSegmentationUtils.parseSegmentsFromModelOutput("not-json", "完整回复", 8);
        assertEquals(List.of("完整回复"), segments);
    }

    @Test
    void parseSegmentsFromModelOutput_respectsMaxSegments() {
        String raw = "[\"a\", \"b\", \"c\", \"d\"]";
        List<String> segments = ChatSegmentationUtils.parseSegmentsFromModelOutput(raw, "fallback", 3);
        assertEquals(3, segments.size());
        assertEquals("a", segments.get(0));
        assertEquals("b", segments.get(1));
        assertEquals("cd", segments.get(2));
    }

    @Test
    void buildDelaySchedule_firstSegmentHasZeroDelay() {
        List<Long> delays = ChatSegmentationUtils.buildDelaySchedule(
                List.of("第一段", "第二段"),
                0.35,
                0.015,
                1.2
        );
        assertEquals(2, delays.size());
        assertEquals(0L, delays.get(0));
        assertTrue(delays.get(1) >= 350);
    }

    @Test
    void splitByPunctuationFallback_splitsOnChinesePunctuation() {
        List<String> segments = ChatSegmentationUtils.splitByPunctuationFallback(
                "你好呀。今天怎么样？我挺好的！",
                8
        );
        assertEquals(3, segments.size());
        assertEquals("你好呀。", segments.get(0));
        assertEquals("今天怎么样？", segments.get(1));
        assertEquals("我挺好的！", segments.get(2));
    }

    @Test
    void splitByPunctuationFallback_returnsSingleWhenNoBreak() {
        List<String> segments = ChatSegmentationUtils.splitByPunctuationFallback("短句无标点", 8);
        assertEquals(List.of("短句无标点"), segments);
    }
}
