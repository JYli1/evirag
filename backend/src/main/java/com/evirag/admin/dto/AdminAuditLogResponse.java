package com.evirag.admin.dto;

import com.evirag.admin.AdminAuditLog;
import java.time.Instant;

/**
 * 管理员审计日志响应。
 *
 * <p>detail 是脱敏后的 JSON 文本，前端可按只读详情展示。</p>
 */
public record AdminAuditLogResponse(
        Long id,
        Long adminUserId,
        String action,
        String targetType,
        Long targetId,
        String detail,
        String ipAddress,
        String userAgent,
        Instant createdAt
) {

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
