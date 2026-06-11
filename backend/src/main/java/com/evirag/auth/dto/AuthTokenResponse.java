package com.evirag.auth.dto;

/**
 * 认证成功响应。
 */
public record AuthTokenResponse(
        // 前端后续请求会把该 token 放到 Authorization: Bearer 中。
        String token,
        // 秒级过期时间戳，前端可据此判断是否需要重新登录。
        long expiresAt,
        // 当前登录用户的安全展示信息，不包含 passwordHash。
        UserResponse user
) {
}
