package com.evirag.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 邮箱验证码注册请求。
 */
public record RegisterRequest(
        // 注册邮箱会先统一 trim + 小写，避免同一个邮箱大小写不同导致重复账号。
        @NotBlank @Email String email,
        // 这里只校验长度；真正保存前会在 AuthService 中用 BCrypt 加密。
        @NotBlank @Size(min = 8, max = 128) String password,
        // 验证码固定 6 位数字，和 EmailVerificationService 中保存的验证码规则保持一致。
        @NotBlank @Size(min = 6, max = 6) @Pattern(regexp = "\\d{6}") String code
) {
}
