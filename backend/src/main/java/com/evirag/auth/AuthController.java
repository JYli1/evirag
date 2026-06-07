package com.evirag.auth;

import com.evirag.auth.dto.AuthTokenResponse;
import com.evirag.auth.dto.LoginRequest;
import com.evirag.auth.dto.PasswordResetRequest;
import com.evirag.auth.dto.RegisterRequest;
import com.evirag.auth.dto.SendCodeRequest;
import com.evirag.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 *
 * <p>所有接口返回统一 ApiResponse；认证失败和验证码异常交给全局异常处理器转换为统一错误结构。</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;

    public AuthController(AuthService authService, EmailVerificationService emailVerificationService) {
        this.authService = authService;
        this.emailVerificationService = emailVerificationService;
    }

    @PostMapping("/register/send-code")
    public ApiResponse<Void> sendRegisterCode(@Valid @RequestBody SendCodeRequest request, HttpServletRequest servletRequest) {
        emailVerificationService.sendCode(request.email(), VerificationPurpose.REGISTER, clientIp(servletRequest));
        return ApiResponse.success();
    }

    @PostMapping("/register")
    public ApiResponse<AuthTokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/password/send-code")
    public ApiResponse<Void> sendPasswordResetCode(@Valid @RequestBody SendCodeRequest request, HttpServletRequest servletRequest) {
        emailVerificationService.sendCode(request.email(), VerificationPurpose.PASSWORD_RESET, clientIp(servletRequest));
        return ApiResponse.success();
    }

    @PostMapping("/password/reset")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        authService.resetPassword(request);
        return ApiResponse.success();
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
