package com.evirag.auth.dto;

import com.evirag.user.User;

/**
 * 登录态用户响应。
 *
 * <p>响应中不包含 passwordHash、状态等内部字段，避免前端或日志暴露敏感信息。</p>
 */
public record UserResponse(Long id, String email, String role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole());
    }
}
