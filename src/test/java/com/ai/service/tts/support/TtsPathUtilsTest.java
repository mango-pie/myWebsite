package com.ai.service.tts.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TtsPathUtilsTest {

    @Test
    void normalizePath_backslashToForward() {
        assertEquals("E:/Quark/gpt-vot/ref.wav", TtsPathUtils.normalizePath("E:\\Quark\\gpt-vot\\ref.wav"));
    }

    @Test
    void normalizePath_trim() {
        assertEquals("E:/a.wav", TtsPathUtils.normalizePath("  E:/a.wav  "));
    }
}
