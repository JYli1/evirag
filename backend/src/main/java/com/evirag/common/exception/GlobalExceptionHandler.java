package com.evirag.common.exception;

import com.evirag.common.api.ApiErrorCode;
import com.evirag.common.api.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * REST 全局异常处理器。
 *
 * <p>该类把常见框架异常统一转换为 {@link ApiResponse}，保证前端无论遇到校验错误、认证失败还是系统异常，
 * 都能拿到固定的 success/code/message/data 结构。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理 @Valid 标注的请求体校验失败，优先返回字段名和默认错误消息。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        String globalErrors = ex.getBindingResult().getGlobalErrors().stream()
                .map(this::formatObjectError)
                .collect(Collectors.joining("; "));
        String message = joinMessages(fieldErrors, globalErrors);
        return build(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED, message);
    }

    /**
     * 处理请求参数、路径变量等 Jakarta Validation 约束失败。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED, message);
    }

    /**
     * 处理 JSON 语法错误、字段类型不匹配等请求体读取失败。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, ApiErrorCode.BAD_REQUEST.getMessage());
    }

    /**
     * 处理 Spring Security 认证失败。
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return build(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED.getMessage());
    }

    /**
     * 处理已登录但权限不足的访问。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN, ApiErrorCode.FORBIDDEN.getMessage());
    }

    /**
     * 兜底处理未显式分类的异常；不把异常堆栈和敏感配置暴露给前端。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("未处理的后端异常", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorCode.INTERNAL_ERROR, ApiErrorCode.INTERNAL_ERROR.getMessage());
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    private String formatObjectError(ObjectError error) {
        return error.getObjectName() + ": " + error.getDefaultMessage();
    }

    private String joinMessages(String fieldErrors, String globalErrors) {
        if (fieldErrors == null || fieldErrors.isBlank()) {
            return globalErrors;
        }
        if (globalErrors == null || globalErrors.isBlank()) {
            return fieldErrors;
        }
        return fieldErrors + "; " + globalErrors;
    }

    private ResponseEntity<ApiResponse<Void>> build(HttpStatus status, ApiErrorCode code, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(code, message));
    }
}
