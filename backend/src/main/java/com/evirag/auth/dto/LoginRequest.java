package com.evirag.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 邮箱密码登录请求。
 */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {
}
