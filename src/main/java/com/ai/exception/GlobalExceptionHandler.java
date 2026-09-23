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
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
        return systemErrorResponse(e);
    }

    /**
     * 兜底：受检异常等其余异常，避免走容器 /error 返回非 BaseResponse 格式。
     */
    @ExceptionHandler(Exception.class)
    public BaseResponse<?> exceptionHandler(Exception e) {
        log.error("Unexpected exception", e);
        return systemErrorResponse(e);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public BaseResponse<?> methodArgumentNotValidHandler(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .findFirst()
                .orElse("");
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, detail.isBlank() ? "请求参数错误" : detail);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            MaxUploadSizeExceededException.class})
    public BaseResponse<?> badRequestHandler(Exception e) {
        log.warn("Bad request: {}", e.getMessage());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求格式错误或超出限制");
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public BaseResponse<?> notFoundHandler(Exception e) {
        return ResultUtils.error(ErrorCode.NOT_FOUND_ERROR, "资源不存在");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public BaseResponse<?> methodNotSupportedHandler(HttpRequestMethodNotSupportedException e) {
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求方法不支持");
    }

    private BaseResponse<?> systemErrorResponse(Exception e) {
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
