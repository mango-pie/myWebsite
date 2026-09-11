package com.ai.interceptor;

import com.ai.constant.SiteSettingConstant;
import com.ai.constant.UserConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.model.entity.User;
import com.ai.model.enums.UserRoleEnum;
import com.ai.service.SiteSettingService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 维护模式：非管理员禁止写操作。
 */
@Component
public class MaintenanceModeInterceptor implements HandlerInterceptor {

    @Resource
    private SiteSettingService siteSettingService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method)
                || "HEAD".equalsIgnoreCase(method)
                || "OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        boolean maintenance = siteSettingService.getBool(
                SiteSettingConstant.MODULE_SITE, "maintenance_mode", false);
        if (!maintenance) {
            return true;
        }

        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        if (path.startsWith("/user/login")
                || path.startsWith("/user/register")
                || path.startsWith("/admin/site-settings")) {
            return true;
        }

        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        if (userObj instanceof User user) {
            UserRoleEnum role = UserRoleEnum.getEnumByValue(user.getUserRole());
            if (UserRoleEnum.ADMIN.equals(role) || UserRoleEnum.ADMINISTRATOR.equals(role)) {
                return true;
            }
        }

        throw new BusinessException(ErrorCode.MAINTENANCE_ERROR);
    }
}
