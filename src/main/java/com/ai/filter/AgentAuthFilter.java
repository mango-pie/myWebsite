package com.ai.filter;

import com.ai.config.SiteAgentAuthProperties;
import com.ai.constant.UserConstant;
import com.ai.model.entity.User;
import com.ai.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 用环境变量里的 Agent Token 把请求视为对应用户已登录（供本机 AI / 脚本调用）。
 * <p>
 * 登录态只写入 request 属性（{@link #AGENT_LOGIN_STATE}），由
 * {@code UserServiceImpl#getLoginUser} 优先读取——不落 HttpSession，
 * token 轮换后不会有残留 Cookie 继续生效；鉴权用户不存在时 fail-closed。
 * 可用 {@code app.agent.path-prefixes} 把令牌限制在指定路径前缀内。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AgentAuthFilter extends OncePerRequestFilter {

    public static final String HEADER_AGENT_TOKEN = "X-Agent-Token";

    @Resource(name = "siteAgentAuthProperties")
    private SiteAgentAuthProperties siteAgentAuthProperties;

    @Resource
    private UserService userService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !siteAgentAuthProperties.enabled();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (siteAgentAuthProperties.allowsPath(request.getRequestURI())) {
            String provided = extractToken(request);
            if (StringUtils.hasText(provided) && tokenMatches(provided)) {
                Long userId = siteAgentAuthProperties.parsedUserId();
                if (userId != null) {
                    try {
                        User user = userService.getById(userId);
                        if (user != null && user.getId() != null) {
                            request.setAttribute(UserConstant.AGENT_LOGIN_STATE, user);
                        } else {
                            log.warn("Agent token user not found: {}", userId);
                        }
                    } catch (Exception e) {
                        // filter 层不走 GlobalExceptionHandler，吞掉异常按未鉴权放行后续链路自行拦截
                        log.warn("Agent token auth failed: {}", e.getMessage());
                    }
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private static String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.length() > 7
                && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return header.substring(7).trim();
        }
        String alt = request.getHeader(HEADER_AGENT_TOKEN);
        return alt == null ? "" : alt.trim();
    }

    private boolean tokenMatches(String provided) {
        byte[] expected = siteAgentAuthProperties.getToken().getBytes(StandardCharsets.UTF_8);
        byte[] got = provided.getBytes(StandardCharsets.UTF_8);
        if (expected.length == 0 || expected.length != got.length) {
            return false;
        }
        return MessageDigest.isEqual(expected, got);
    }
}
