package com.evirag.auth.dto;

/**
 * 认证成功响应。
 */
public record AuthTokenResponse(String token, long expiresAt, UserResponse user) {
}
