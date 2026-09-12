package com.ai.filter;

import cn.hutool.core.util.StrUtil;
import com.ai.constant.PetAuthConstant;
import com.ai.model.entity.User;
import com.ai.service.PetDeviceService;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 解析 /pet/** 请求上的 Bearer 设备令牌，写入 request attribute，供 UserService 识别登录用户。
 * 设备绑定/列表仍走 Session；聊天与日记桥接走 Token。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 40)
public class PetDeviceAuthFilter extends OncePerRequestFilter {

    @Resource
    private PetDeviceService petDeviceService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path == null || !path.startsWith("/pet/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String auth = request.getHeader("Authorization");
        if (StrUtil.isNotBlank(auth) && auth.regionMatches(true, 0, PetAuthConstant.BEARER_PREFIX, 0,
                PetAuthConstant.BEARER_PREFIX.length())) {
            String raw = auth.substring(PetAuthConstant.BEARER_PREFIX.length()).trim();
            User user = petDeviceService.resolveUserByRawToken(raw);
            if (user != null) {
                request.setAttribute(PetAuthConstant.PET_LOGIN_USER_ATTR, user);
            }
        }
        filterChain.doFilter(request, response);
    }
}
