package com.evirag.admin.dto;

import com.evirag.admin.AdminAuditLog;
import java.time.Instant;

/**
 * 管理员审计日志响应。
 *
 * <p>detail 是脱敏后的 JSON 文本，前端可按只读详情展示。</p>
 */
public record AdminAuditLogResponse(
        // 审计日志主键。
        Long id,
        // 执行动作的管理员用户 ID。
        Long adminUserId,
        // 动作类型，例如 UPDATE_USER_STATUS。
        String action,
        // 操作对象类型，例如 USER。
        String targetType,
        // 操作对象 ID，部分系统级操作可以为空。
        Long targetId,
        // JSON 文本详情，只存业务字段变化，不存密码或 token。
        String detail,
        // 管理员请求来源 IP。
        String ipAddress,
        // 管理员浏览器 User-Agent。
        String userAgent,
        // 操作时间。
        Instant createdAt
) {

    /**
     * 将审计实体转换为只读响应对象。
     */
    public static AdminAuditLogResponse from(AdminAuditLog log) {
        return new AdminAuditLogResponse(
                log.getId(),
                log.getAdminUserId(),
                log.getAction(),
                log.getTargetType(),
                log.getTargetId(),
                log.getDetail(),
                log.getIpAddress(),
                log.getUserAgent(),
                log.getCreatedAt()
        );
    }
}
