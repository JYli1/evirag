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
        if (userRepository.existsByEmail(email)) {
            throw new AuthException("注册信息无效");
        }
        emailVerificationService.verifyCode(email, VerificationPurpose.REGISTER, request.code());
        User user = userRepository.save(User.create(email, passwordEncoder.encode(request.password())));
        return issueToken(user);
    }

    @Transactional(readOnly = true)
    public AuthTokenResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException(GENERIC_LOGIN_FAILURE));
        if (!user.isActive() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AuthException(GENERIC_LOGIN_FAILURE);
        }
        return issueToken(user);
    }

    @Transactional
    public void resetPassword(PasswordResetRequest request) {
        String email = normalizeEmail(request.email());
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
        return email == null ? "" : email.trim().toLowerCase();
    }
}
