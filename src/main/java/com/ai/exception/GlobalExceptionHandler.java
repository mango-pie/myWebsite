package com.ai.exception;

import com.ai.common.BaseResponse;
import com.ai.common.ResultUtils;
import com.ai.constant.UserConstant;
import com.ai.model.entity.User;
import com.ai.model.enums.UserRoleEnum;
import com.ai.setting.runtime.OpsRuntimeSettings;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Hidden
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @Resource
    private OpsRuntimeSettings opsRuntimeSettings;

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException", e);
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        if (opsRuntimeSettings.debugExposeErrorDetail() && isCurrentUserAdmin()) {
            String detail = e.getMessage();
            if (detail == null || detail.isBlank()) {
                detail = e.getClass().getSimpleName();
            }
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误: " + detail);
        }
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }

    private boolean isCurrentUserAdmin() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return false;
            }
            HttpServletRequest request = attrs.getRequest();
            Object loginObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
            if (!(loginObj instanceof User user)) {
                return false;
            }
            UserRoleEnum role = UserRoleEnum.getEnumByValue(user.getUserRole());
            return UserRoleEnum.ADMIN.equals(role) || UserRoleEnum.ADMINISTRATOR.equals(role);
        } catch (Exception ignored) {
            return false;
        }
    }
}
