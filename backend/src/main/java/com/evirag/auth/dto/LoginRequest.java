package com.evirag.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 邮箱密码登录请求。
 */
public record LoginRequest(
        // 邮箱是系统唯一登录标识；@Email 会在 Controller 方法执行前完成格式校验。
        @NotBlank @Email String email,
        // 明文密码只在请求 DTO 中短暂停留，进入 AuthService 后会用 BCrypt 和数据库哈希比对。
        @NotBlank String password
) {
}
