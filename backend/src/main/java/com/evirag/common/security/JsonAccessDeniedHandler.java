package com.evirag.common.security;

import com.evirag.common.api.ApiErrorCode;
import com.evirag.common.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Spring Security 无权限 JSON 响应处理器。
 *
 * <p>授权失败同样发生在过滤器链阶段，不能依赖 RestControllerAdvice。
 * 本类只负责输出统一 JSON，完整的安全规则仍留给 Task 3 的 SecurityConfig 组装。</p>
 */
@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JsonAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 写出通用无权限错误；不使用 accessDeniedException.getMessage()，避免泄露权限规则或资源信息。
     */
    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException, ServletException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // 这里直接写 response 输出流，因为请求还没有进入 Controller 层。
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(ApiErrorCode.FORBIDDEN));
    }
}
