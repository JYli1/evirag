package com.evirag.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 邮箱验证码发送请求。
 */
public record SendCodeRequest(@NotBlank @Email String email) {
}
