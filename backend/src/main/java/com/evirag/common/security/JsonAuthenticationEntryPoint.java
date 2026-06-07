package com.evirag.common.security;

import com.evirag.common.api.ApiErrorCode;
import com.evirag.common.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Spring Security 未认证 JSON 响应处理器。
 *
 * <p>认证异常发生在过滤器链阶段，不会进入 Controller，也就不会被 GlobalExceptionHandler 捕获。
 * Task 3 创建 SecurityConfig 时可直接注入本类，确保未登录响应仍然使用统一 ApiResponse 结构。</p>
 */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 写出通用未认证错误；不使用 authException.getMessage()，避免把 token、用户名或内部认证细节暴露给前端。
     */
    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException, ServletException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(ApiErrorCode.UNAUTHORIZED));
    }
}
