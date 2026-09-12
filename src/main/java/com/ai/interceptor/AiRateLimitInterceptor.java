package com.ai.interceptor;

import com.ai.config.AiRateLimitProperties;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.model.entity.User;
import com.ai.service.UserService;
import com.ai.utils.RequestClientUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI 端点限流：固定窗口计数，键为「规则 + 登录用户 / 来源 IP」。
 * 只拦配置声明的高成本路径（LLM/抓取），普通接口不受影响；
 * 超限抛 TOO_MANY_REQUESTS，由全局异常处理器返回统一响应体。
 */
@Slf4j
@Component
public class AiRateLimitInterceptor implements HandlerInterceptor {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    /** 计数器上限：防恶意 IP 刷爆内存，超过后清理过期窗口 */
    private static final int MAX_COUNTERS = 50_000;

    private final Map<String, FixedWindowCounter> counters = new ConcurrentHashMap<>();

    @Resource
    private AiRateLimitProperties properties;

    @Resource
    private UserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!properties.isEnabled() || properties.getRules().isEmpty()) {
            return true;
        }
        String path = stripContextPath(request);
        AiRateLimitProperties.Rule rule = firstMatch(path);
        if (rule == null || rule.getLimit() <= 0) {
            return true;
        }
        String identity = resolveIdentity(request);
        String key = rule.getPattern() + "#" + identity;
        long windowMillis = Math.max(1, rule.getWindowSeconds()) * 1000L;
        if (!tryAcquire(key, windowMillis, rule.getLimit())) {
            log.warn("AI rate limit hit: path={}, identity={}, limit={}/{}s",
                    path, identity, rule.getLimit(), rule.getWindowSeconds());
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }
        return true;
    }

    private AiRateLimitProperties.Rule firstMatch(String path) {
        List<AiRateLimitProperties.Rule> rules = properties.getRules();
        for (AiRateLimitProperties.Rule rule : rules) {
            if (PATH_MATCHER.match(rule.getPattern(), path)) {
                return rule;
            }
        }
        return null;
    }

    /**
     * 登录用户按用户号限（共享 IP 的登录用户互不影响）；未登录按来源 IP。
     */
    private String resolveIdentity(HttpServletRequest request) {
        try {
            User user = userService.getLoginUser(request);
            if (user != null && user.getId() != null) {
                return "u" + user.getId();
            }
        } catch (Exception ignored) {
            // 未登录：回落到来源 IP
        }
        return "ip:" + RequestClientUtils.getClientIp(request);
    }

    private boolean tryAcquire(String key, long windowMillis, int limit) {
        if (counters.size() > MAX_COUNTERS) {
            evictStale(windowMillis);
        }
        long window = System.currentTimeMillis() / windowMillis;
        FixedWindowCounter counter = counters.computeIfAbsent(key, k -> new FixedWindowCounter());
        synchronized (counter) {
            if (counter.window != window) {
                counter.window = window;
                counter.count.set(0);
            }
            return counter.count.incrementAndGet() <= limit;
        }
    }

    private void evictStale(long windowMillis) {
        long window = System.currentTimeMillis() / windowMillis;
        counters.entrySet().removeIf(e -> e.getValue().window != window);
    }

    private String stripContextPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return path;
    }

    private static final class FixedWindowCounter {
        volatile long window;
        final AtomicLong count = new AtomicLong();
    }
}
