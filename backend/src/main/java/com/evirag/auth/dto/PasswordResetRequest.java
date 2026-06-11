package com.evirag.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 密码重置请求。
 */
public record PasswordResetRequest(
        // 需要重置密码的邮箱，后端会先确认验证码属于这个邮箱和重置密码用途。
        @NotBlank @Email String email,
        // 新密码不会明文入库，AuthService 会重新生成 passwordHash。
        @NotBlank @Size(min = 8, max = 128) String newPassword,
        // 只有验证码校验通过才允许修改密码，避免仅凭邮箱就能重置。
        @NotBlank @Size(min = 6, max = 6) @Pattern(regexp = "\\d{6}") String code
) {
}
