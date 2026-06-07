package com.evirag.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import com.evirag.auth.dto.AuthTokenResponse;
import com.evirag.auth.dto.LoginRequest;
import com.evirag.auth.dto.RegisterRequest;
import com.evirag.user.User;
import com.evirag.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 注册与登录核心流程测试。
 *
 * <p>测试只覆盖 AuthService 的业务边界：验证码注册、密码哈希、邮箱密码登录、禁用账号拒绝登录。
 * JWT 生成使用真实 JwtService，验证码服务和仓储使用 mock，以便失败原因能精确落在认证逻辑上。</p>
 */
class AuthServiceTest {

    private static final String EMAIL = "user@example.com";

    private UserRepository userRepository;
    private EmailVerificationService emailVerificationService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = org.mockito.Mockito.mock(UserRepository.class);
        emailVerificationService = org.mockito.Mockito.mock(EmailVerificationService.class);
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        JwtService jwtService = new JwtService(
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                60,
                Clock.fixed(Instant.parse("2026-06-08T08:00:00Z"), ZoneOffset.UTC)
        );
        authService = new AuthService(userRepository, emailVerificationService, passwordEncoder, jwtService);
    }

    /**
     * 注册时必须先消费邮箱验证码，然后使用 BCrypt 保存密码哈希，不能明文落库。
     */
    @Test
    void registersUserWithEmailCodeAndBcryptPassword() {
        RegisterRequest request = new RegisterRequest(EMAIL, "StrongPass123", "123456");
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(userRepository.save(org.mockito.ArgumentMatchers.any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    user.setId(7L);
                    return user;
                });

        AuthTokenResponse response = authService.register(request);

        verify(emailVerificationService).verifyCode(EMAIL, VerificationPurpose.REGISTER, "123456");
        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(user ->
                EMAIL.equals(user.getEmail())
                        && !"StrongPass123".equals(user.getPasswordHash())
                        && new BCryptPasswordEncoder().matches("StrongPass123", user.getPasswordHash())
        ));
        assertThat(response.token()).isNotBlank();
        assertThat(response.user().email()).isEqualTo(EMAIL);
    }

    /**
     * 注册验证码错误时必须先失败在验证码校验处，不能继续查邮箱是否已注册。
     */
    @Test
    void wrongCodeRegistrationDoesNotCheckExistingEmail() {
        RegisterRequest request = new RegisterRequest(EMAIL, "StrongPass123", "000000");
        doThrow(new VerificationCodeException("验证码无效或已过期"))
                .when(emailVerificationService)
                .verifyCode(EMAIL, VerificationPurpose.REGISTER, "000000");

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(VerificationCodeException.class)
                .hasMessageContaining("验证码无效或已过期");

        verify(userRepository, never()).existsByEmail(EMAIL);
    }

    /**
     * 已注册且状态正常的用户，可以使用邮箱和密码登录并获得 JWT。
     */
    @Test
    void logsInWithEmailAndPassword() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        User user = User.create(EMAIL, encoder.encode("StrongPass123"));
        user.setId(9L);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        AuthTokenResponse response = authService.login(new LoginRequest(EMAIL, "StrongPass123"));

        assertThat(response.token()).isNotBlank();
        assertThat(response.user().id()).isEqualTo(9L);
        assertThat(response.user().role()).isEqualTo("USER");
    }

    /**
     * 禁用账号即使密码正确也必须拒绝登录，返回通用认证失败异常，避免泄露账号状态细节。
     */
    @Test
    void rejectsDisabledUserLogin() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        User user = User.create(EMAIL, encoder.encode("StrongPass123"));
        user.setStatus("DISABLED");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "StrongPass123")))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("邮箱或密码错误");
    }
}
