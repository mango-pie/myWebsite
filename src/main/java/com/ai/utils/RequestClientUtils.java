package com.ai.utils;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 解析。
 */
public final class RequestClientUtils {

    private static final int MAX_IP_LEN = 64;

    private RequestClientUtils() {
    }

    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ip = firstIp(request.getHeader("X-Forwarded-For"));
        if (StrUtil.isBlank(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (StrUtil.isBlank(ip)) {
            ip = request.getRemoteAddr();
        }
        return truncate(ip, MAX_IP_LEN);
    }

    private static String firstIp(String forwarded) {
        if (StrUtil.isBlank(forwarded)) {
            return null;
        }
        int comma = forwarded.indexOf(',');
        return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
