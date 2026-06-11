package com.evirag.auth;

import com.evirag.auth.JwtService.AuthJwt;
import com.evirag.auth.dto.AuthTokenResponse;
import com.evirag.auth.dto.LoginRequest;
import com.evirag.auth.dto.PasswordResetRequest;
import com.evirag.auth.dto.RegisterRequest;
import com.evirag.auth.dto.UserResponse;
import com.evirag.user.User;
import com.evirag.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 注册、登录和密码重置服务。
 *
 * <p>服务层统一做邮箱规范化、密码哈希和登录失败文案处理，避免 Controller 中散落认证安全规则。</p>
 */
@Service
public class AuthService {

    private static final String GENERIC_LOGIN_FAILURE = "邮箱或密码错误";

    private final UserRepository userRepository;
    private final EmailVerificationService emailVerificationService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            EmailVerificationService emailVerificationService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.emailVerificationService = emailVerificationService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthTokenResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        // 注册必须先消费验证码，防止没有邮箱所有权就创建账号。
        emailVerificationService.verifyCode(email, VerificationPurpose.REGISTER, request.code());
        if (userRepository.existsByEmail(email)) {
            // 不直接说“邮箱已存在”，降低账号枚举风险。
            throw new AuthException("注册信息无效");
        }
        // passwordEncoder 是 BCryptPasswordEncoder，保存的是哈希而不是明文密码。
        User user = userRepository.save(User.create(email, passwordEncoder.encode(request.password())));
        return issueToken(user);
    }

    @Transactional(readOnly = true)
    public AuthTokenResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException(GENERIC_LOGIN_FAILURE));
        if (!user.isActive() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            // 邮箱不存在、密码错误、账号禁用都返回同一文案，避免暴露账号状态。
            throw new AuthException(GENERIC_LOGIN_FAILURE);
        }
        return issueToken(user);
    }

    @Transactional
    public void resetPassword(PasswordResetRequest request) {
        String email = normalizeEmail(request.email());
        // 重置密码验证码和注册验证码用途不同，不能混用。
        emailVerificationService.verifyCode(email, VerificationPurpose.PASSWORD_RESET, request.code());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException("密码重置请求无效"));
        if (!user.isActive()) {
            throw new AuthException("密码重置请求无效");
        }
        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    private AuthTokenResponse issueToken(User user) {
        AuthJwt jwt = jwtService.createToken(user);
        return new AuthTokenResponse(jwt.token(), jwt.expiresAt(), UserResponse.from(user));
    }

    private String normalizeEmail(String email) {
        // 邮箱统一小写，保证登录、注册、重置密码使用同一套匹配规则。
        return email == null ? "" : email.trim().toLowerCase();
    }
}
