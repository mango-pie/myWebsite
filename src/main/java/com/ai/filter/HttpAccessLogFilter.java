package com.ai.filter;

import com.ai.constant.HttpLogModeConstant;
import com.ai.constant.UserConstant;
import com.ai.model.entity.User;
import com.ai.service.HttpAccessLogService;
import com.ai.setting.runtime.OpsRuntimeSettings;
import com.ai.utils.RequestClientUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * HTTP 访问日志采样 Filter（ops-observability Phase D）。
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 20)
public class HttpAccessLogFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_ATTR = "ops.http.traceId";

    private static final List<String> EXCLUDE_PATTERNS = List.of(
            "/doc.html",
            "/webjars/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/uploads/**",
            "/error",
            "/favicon.ico"
    );

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Resource
    private OpsRuntimeSettings opsRuntimeSettings;

    @Resource
    private HttpAccessLogService httpAccessLogService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = servletPath(request);
        for (String pattern : EXCLUDE_PATTERNS) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        request.setAttribute(TRACE_ID_ATTR, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);

        long startNs = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            try {
                maybeRecord(request, response, traceId, startNs);
            } catch (Exception e) {
                log.debug("Http access log skip: {}", e.getMessage());
            }
        }
    }

    private void maybeRecord(HttpServletRequest request, HttpServletResponse response,
                             String traceId, long startNs) {
        if (!opsRuntimeSettings.httpLogEnabled()) {
            return;
        }
        int status = response.getStatus();
        long latencyMs = Math.max(0L, (System.nanoTime() - startNs) / 1_000_000L);
        String mode = String.valueOf(opsRuntimeSettings.httpLogMode()).trim().toLowerCase(Locale.ROOT);
        if (!shouldRecord(mode, status, latencyMs)) {
            return;
        }

        String path = servletPath(request);
        String errorSummary = status >= 400 ? ("HTTP " + status) : null;
        httpAccessLogService.record(
                request.getMethod(),
                path,
                status,
                latencyMs,
                resolveUserId(request),
                RequestClientUtils.getClientIp(request),
                traceId,
                errorSummary
        );
    }

    private boolean shouldRecord(String mode, int status, long latencyMs) {
        if (HttpLogModeConstant.ALL.equals(mode)) {
            return true;
        }
        if (HttpLogModeConstant.SLOW_AND_ERRORS.equals(mode)) {
            return status >= 400 || latencyMs >= opsRuntimeSettings.httpLogSlowMs();
        }
        // errors_only（默认）
        return status >= 400;
    }

    private Long resolveUserId(HttpServletRequest request) {
        try {
            HttpSession session = request.getSession(false);
            if (session == null) {
                return null;
            }
            Object userObj = session.getAttribute(UserConstant.USER_LOGIN_STATE);
            if (userObj instanceof User user) {
                return user.getId();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String servletPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri != null && uri.startsWith(context)) {
            uri = uri.substring(context.length());
        }
        if (uri == null || uri.isEmpty()) {
            uri = "/";
        }
        int q = uri.indexOf('?');
        if (q >= 0) {
            uri = uri.substring(0, q);
        }
        return uri.length() <= 512 ? uri : uri.substring(0, 512);
    }
}
