package com.evirag.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 管理员更新用户状态请求。
 *
 * <p>当前 demo 只允许 ACTIVE 和 DISABLED 两种状态，避免前端传入未定义状态导致安全过滤器行为不一致。</p>
 */
public record UpdateUserStatusRequest(
        // 只允许这两个状态，防止前端传入拼写错误或数据库未定义状态。
        @NotBlank(message = "用户状态不能为空")
        @Pattern(regexp = "ACTIVE|DISABLED", message = "用户状态只能是 ACTIVE 或 DISABLED")
        String status
) {
}
