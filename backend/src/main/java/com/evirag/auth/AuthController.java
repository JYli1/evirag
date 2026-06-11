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

    /**
     * 注册前发送邮箱验证码。
     *
     * <p>验证码用途固定为 REGISTER，后端不会相信前端传入的用途字段。</p>
     */
    @PostMapping("/register/send-code")
    public ApiResponse<Void> sendRegisterCode(@Valid @RequestBody SendCodeRequest request, HttpServletRequest servletRequest) {
        emailVerificationService.sendCode(request.email(), VerificationPurpose.REGISTER, clientIp(servletRequest));
        return ApiResponse.success();
    }

    /**
     * 注册账号并直接签发 JWT。
     */
    @PostMapping("/register")
    public ApiResponse<AuthTokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    /**
     * 邮箱密码登录。
     */
    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    /**
     * 找回密码前发送验证码。
     */
    @PostMapping("/password/send-code")
    public ApiResponse<Void> sendPasswordResetCode(@Valid @RequestBody SendCodeRequest request, HttpServletRequest servletRequest) {
        emailVerificationService.sendCode(request.email(), VerificationPurpose.PASSWORD_RESET, clientIp(servletRequest));
        return ApiResponse.success();
    }

    /**
     * 校验验证码后修改密码。
     */
    @PostMapping("/password/reset")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        authService.resetPassword(request);
        return ApiResponse.success();
    }

    private String clientIp(HttpServletRequest request) {
        // 这里只记录首个 X-Forwarded-For 作为审计线索；安全决策仍不能依赖该值，除非部署层已配置可信反向代理。
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
