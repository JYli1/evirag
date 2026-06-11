package com.evirag.auth.dto;

import com.evirag.user.User;

/**
 * 登录态用户响应。
 *
 * <p>响应中不包含 passwordHash、状态等内部字段，避免前端或日志暴露敏感信息。</p>
 */
public record UserResponse(
        // 数据库用户主键，前端只用于展示和管理端定位。
        Long id,
        // 用户邮箱，也作为登录账号。
        String email,
        // USER 或 ADMIN，前端用它控制是否显示管理员入口。
        String role
) {

    /**
     * 从 JPA 实体转换为响应对象。
     *
     * <p>这样 Controller 不会直接返回 User 实体，也就不会把 passwordHash 等内部字段序列化出去。</p>
     */
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole());
    }
}
