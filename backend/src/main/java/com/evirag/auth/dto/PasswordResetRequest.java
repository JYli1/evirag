package com.evirag.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 密码重置请求。
 */
public record PasswordResetRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 128) String newPassword,
        @NotBlank @Size(min = 6, max = 6) String code
) {
}
