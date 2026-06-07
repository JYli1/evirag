package com.evirag.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evirag.auth.dto.RegisterRequest;
import com.evirag.user.User;
import com.evirag.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口与 Spring Security 过滤器链集成测试。
 *
 * <p>这些用例启动真实 MVC 和安全过滤器链，用 mock 仓储隔离数据库，用 mock 邮件发送器避免 SMTP 副作用。</p>
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
        "evirag.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
})
@AutoConfigureMockMvc
class AuthSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private EmailVerificationCodeRepository emailVerificationCodeRepository;

    @MockBean
    private VerificationEmailSender verificationEmailSender;

    @BeforeEach
    void setUp() {
        when(emailVerificationCodeRepository.saveAndFlush(any(EmailVerificationCode.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    /**
     * 已注册邮箱和未注册邮箱在“验证码错误”注册尝试下，必须返回完全相同的状态、code 和 message。
     */
    @Test
    void registrationWrongCodeDoesNotEnumerateExistingEmail() throws Exception {
        doThrow(new VerificationCodeException("验证码无效或已过期"))
                .when(emailVerificationCodeRepository)
                .findLatestUsable(any(), any());
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);

        String existingBody = registerAndReadBody(new RegisterRequest("existing@example.com", "StrongPass123", "000000"));
        String newBody = registerAndReadBody(new RegisterRequest("new@example.com", "StrongPass123", "000000"));

        JsonNode existing = objectMapper.readTree(existingBody);
        JsonNode fresh = objectMapper.readTree(newBody);
        assertThat(existing.get("success").asBoolean()).isFalse();
        assertThat(existing.get("code").asText()).isEqualTo(fresh.get("code").asText());
        assertThat(existing.get("message").asText()).isEqualTo(fresh.get("message").asText());
    }

    /**
     * 受保护接口无 token 时返回统一 JSON 401。
     */
    @Test
    void protectedEndpointWithoutTokenReturnsJsonUnauthorized() throws Exception {
        mockMvc.perform(get("/api/test/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    /**
     * USER token 访问管理端占位接口时返回统一 JSON 403。
     */
    @Test
    void userTokenOnAdminEndpointReturnsJsonForbidden() throws Exception {
        User user = activeUser(1L, "user@example.com", "USER");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/admin/test").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    /**
     * ADMIN token 通过角色检查，能到达测试端点。
     */
    @Test
    void adminTokenPassesAdminRoleCheck() throws Exception {
        User admin = activeUser(2L, "admin@example.com", "ADMIN");
        when(userRepository.findById(2L)).thenReturn(Optional.of(admin));

        mockMvc.perform(get("/api/admin/test").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    /**
     * token 里曾经是 ADMIN，但数据库当前角色已降级为 USER 时，应按当前角色拒绝管理端访问。
     */
    @Test
    void demotedUserTokenUsesCurrentDatabaseRole() throws Exception {
        User tokenAdmin = activeUser(5L, "demoted@example.com", "ADMIN");
        User currentUser = activeUser(5L, "demoted@example.com", "USER");
        when(userRepository.findById(5L)).thenReturn(Optional.of(currentUser));

        mockMvc.perform(get("/api/admin/test").header(HttpHeaders.AUTHORIZATION, bearer(tokenAdmin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    /**
     * token 对应用户被禁用后，即使 token 本身还有效，也必须按未认证拒绝。
     */
    @Test
    void disabledUserTokenReturnsJsonUnauthorized() throws Exception {
        User user = activeUser(3L, "disabled@example.com", "USER");
        user.setStatus("DISABLED");
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/test/protected").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    /**
     * 过期或签名被篡改的 token 不能进入受保护接口。
     */
    @Test
    void tamperedTokenReturnsJsonUnauthorized() throws Exception {
        User user = activeUser(4L, "user4@example.com", "USER");
        String tampered = bearer(user);
        tampered = tampered.substring(0, tampered.length() - 2) + "aa";

        mockMvc.perform(get("/api/test/protected").header(HttpHeaders.AUTHORIZATION, tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    /**
     * 已过期 token 不能进入受保护接口。
     */
    @Test
    void expiredTokenReturnsJsonUnauthorized() throws Exception {
        JwtService expiredTokenIssuer = new JwtService(
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                -1,
                Clock.fixed(Instant.parse("2026-06-08T08:00:00Z"), ZoneOffset.UTC)
        );
        User user = activeUser(6L, "expired@example.com", "USER");
        String expired = "Bearer " + expiredTokenIssuer.createToken(user).token();

        mockMvc.perform(get("/api/test/protected").header(HttpHeaders.AUTHORIZATION, expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    /**
     * 公开认证接口不需要 token；这里用无副作用的登录错误路径验证 permitAll 生效。
     */
    @Test
    void publicAuthEndpointRemainsPermitAll() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"none@example.com\",\"password\":\"StrongPass123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verify(userRepository).findByEmail("none@example.com");
    }

    private String registerAndReadBody(RegisterRequest request) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private User activeUser(Long id, String email, String role) {
        User user = User.create(email, "hash");
        user.setId(id);
        user.setRole(role);
        return user;
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.createToken(user).token();
    }

    @TestConfiguration
    static class TestEndpoints {

        @Bean
        TestController testController() {
            return new TestController();
        }
    }

    @RestController
    @RequestMapping
    static class TestController {

        @GetMapping("/api/test/protected")
        java.util.Map<String, Boolean> protectedEndpoint() {
            return java.util.Map.of("ok", true);
        }

        @GetMapping("/api/admin/test")
        java.util.Map<String, Boolean> adminEndpoint() {
            return java.util.Map.of("ok", true);
        }
    }
}
