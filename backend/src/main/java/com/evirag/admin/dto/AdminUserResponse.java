package com.evirag.admin.dto;

import com.evirag.user.User;
import java.time.Instant;

/**
 * 管理员用户列表响应。
 *
 * <p>不包含 passwordHash，只展示管理端需要的身份、角色和状态字段。</p>
 */
public record AdminUserResponse(
        // 用户主键，管理员点进详情或修改状态时使用。
        Long id,
        // 当前 username 默认跟邮箱一致，预留给后续昵称功能。
        String username,
        // 登录邮箱。
        String email,
        // USER 或 ADMIN。
        String role,
        // ACTIVE 表示可登录，DISABLED 表示被禁用。
        String status,
        // 注册时间。
        Instant createdAt,
        // 最近更新用户状态或资料的时间。
        Instant updatedAt
) {

    /**
     * 管理端用户列表也不能直接返回 User 实体，避免 passwordHash 等字段泄露。
     */
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
