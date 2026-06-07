package com.evirag.admin.dto;

import com.evirag.user.User;
import java.time.Instant;

/**
 * 管理员用户列表响应。
 *
 * <p>不包含 passwordHash，只展示管理端需要的身份、角色和状态字段。</p>
 */
public record AdminUserResponse(
        Long id,
        String username,
        String email,
        String role,
        String status,
        Instant createdAt,
        Instant updatedAt
) {

    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
