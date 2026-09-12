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
import org.springframework.dao.DataIntegrityViolationException;
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

    @ExceptionHandler(DataIntegrityViolationException.class)
    public BaseResponse<?> dataIntegrityViolationHandler(DataIntegrityViolationException e) {
        log.error("DataIntegrityViolationException", e);
        String msg = e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage();
        if (msg != null && msg.contains("uk_learning_branch_sibling")) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "同层级下已存在同名主题枝");
        }
        if (msg != null && msg.contains("uk_learning_domain_user_name")) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "已存在同名领域");
        }
        if (opsRuntimeSettings.debugExposeErrorDetail() && isCurrentUserAdmin()) {
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误: " + msg);
        }
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "数据冲突，请检查是否重复创建");
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
