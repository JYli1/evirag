package com.evirag.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 邮箱验证码发送请求。
 */
public record SendCodeRequest(
        // 发送验证码只需要邮箱；用途由 Controller 根据接口路径决定是注册还是重置密码。
        @NotBlank @Email String email
) {
}
