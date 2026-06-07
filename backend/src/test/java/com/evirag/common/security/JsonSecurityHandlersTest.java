package com.evirag.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

/**
 * Security JSON 处理器测试。
 *
 * <p>Spring Security 过滤器链中的认证和授权异常不会进入 ControllerAdvice，
 * 因此这里直接验证过滤器链专用处理器也输出统一 ApiResponse 结构。</p>
 */
class JsonSecurityHandlersTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 验证未认证响应使用统一 JSON 结构，并且只返回通用错误消息。
     */
    @Test
    void authenticationEntryPointWritesUnifiedUnauthorizedResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        JsonAuthenticationEntryPoint entryPoint = new JsonAuthenticationEntryPoint(objectMapper);

        entryPoint.commence(
                new MockHttpServletRequest(),
                response,
                new BadCredentialsException("secret-token-should-not-leak")
        );

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("code").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(body.get("message").asText()).isEqualTo("未认证或登录已失效");
        assertThat(response.getContentAsString()).doesNotContain("secret-token-should-not-leak");
    }

    /**
     * 验证无权限响应使用统一 JSON 结构，并且不暴露内部异常文本。
     */
    @Test
    void accessDeniedHandlerWritesUnifiedForbiddenResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        JsonAccessDeniedHandler handler = new JsonAccessDeniedHandler(objectMapper);

        handler.handle(
                new MockHttpServletRequest(),
                response,
                new AccessDeniedException("admin-only-internal-rule")
        );

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("code").asText()).isEqualTo("FORBIDDEN");
        assertThat(body.get("message").asText()).isEqualTo("没有权限访问该资源");
        assertThat(response.getContentAsString()).doesNotContain("admin-only-internal-rule");
    }
}
