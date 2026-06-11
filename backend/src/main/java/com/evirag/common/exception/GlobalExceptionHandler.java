package com.evirag.common.exception;

import com.evirag.common.api.ApiErrorCode;
import com.evirag.common.api.ApiResponse;
import com.evirag.auth.AuthException;
import com.evirag.auth.VerificationCodeException;
import com.evirag.admin.AdminNotFoundException;
import com.evirag.chat.ChatNotFoundException;
import com.evirag.document.DocumentNotFoundException;
import com.evirag.document.DocumentUploadException;
import com.evirag.knowledge.KnowledgeBaseNotFoundException;
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
import org.springframework.web.multipart.MaxUploadSizeExceededException;
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
     * 处理 Spring/Tomcat 在进入 Controller 前拦截的大文件上传。
     *
     * <p>如果没有这个分支，超过 multipart 限制时会落到兜底异常，前端只能看到“服务器内部错误”。</p>
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        return build(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED, "上传文件超过服务器允许的最大大小");
    }

    /**
     * 处理验证码业务失败，例如限流、过期或错误次数达到上限。
     */
    @ExceptionHandler(VerificationCodeException.class)
    public ResponseEntity<ApiResponse<Void>> handleVerificationCode(VerificationCodeException ex) {
        return build(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED, ex.getMessage());
    }

    /**
     * 处理知识库和文档上传的业务校验失败，例如文件类型不支持或文件过大。
     */
    @ExceptionHandler(DocumentUploadException.class)
    public ResponseEntity<ApiResponse<Void>> handleDocumentUpload(DocumentUploadException ex) {
        return build(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED, ex.getMessage());
    }

    /**
     * 处理普通用户访问不存在或无权访问的知识库/文档，统一返回 404，避免泄露资源归属。
     */
    @ExceptionHandler({
            KnowledgeBaseNotFoundException.class,
            DocumentNotFoundException.class,
            ChatNotFoundException.class,
            AdminNotFoundException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleNotFound(RuntimeException ex) {
        return build(HttpStatus.NOT_FOUND, ApiErrorCode.BAD_REQUEST, ex.getMessage());
    }

    /**
     * 处理注册、登录、密码重置等认证业务失败；登录失败保持通用文案，避免账号枚举。
     */
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuth(AuthException ex) {
        return build(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED, ex.getMessage());
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
        // 字段级错误来自 @NotBlank、@Email、@Size 等注解，例如 email: 不能为空。
        return error.getField() + ": " + error.getDefaultMessage();
    }

    private String formatObjectError(ObjectError error) {
        // 对象级错误来自跨字段校验，例如 AppProperties 中 overlap 必须小于 max。
        return error.getObjectName() + ": " + error.getDefaultMessage();
    }

    private String joinMessages(String fieldErrors, String globalErrors) {
        // 字段错误和对象错误都可能存在，统一拼成前端可以直接展示的一行文本。
        if (fieldErrors == null || fieldErrors.isBlank()) {
            return globalErrors;
        }
        if (globalErrors == null || globalErrors.isBlank()) {
            return fieldErrors;
        }
        return fieldErrors + "; " + globalErrors;
    }

    private ResponseEntity<ApiResponse<Void>> build(HttpStatus status, ApiErrorCode code, String message) {
        // ResponseEntity 负责 HTTP 状态码，ApiResponse 负责业务错误码，两者一起给前端完整语义。
        return ResponseEntity.status(status).body(ApiResponse.error(code, message));
    }
}
