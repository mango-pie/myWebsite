package com.ai.service.tts.support;

import cn.hutool.core.util.StrUtil;

/**
 * GPT-SoVITS 参考音频路径规范化（统一正斜杠，避免缓存失效）
 */
public final class TtsPathUtils {

    private TtsPathUtils() {
    }

    public static String normalizePath(String path) {
        if (StrUtil.isBlank(path)) {
            return path;
        }
        return path.trim().replace('\\', '/');
    }
}
