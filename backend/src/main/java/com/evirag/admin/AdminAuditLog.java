package com.evirag.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 管理员审计日志实体。
 *
 * <p>管理员启停用用户、后续删除知识库或触发重建索引等高权限动作，都应写入该表，方便课程演示和后续开源版本排查操作来源。</p>
 */
@Entity
@Table(name = "admin_audit_logs")
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_user_id", nullable = false)
    private Long adminUserId;

    @Column(nullable = false, length = 128)
    private String action;

    @Column(name = "target_type", nullable = false, length = 64)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(columnDefinition = "JSON")
    private String detail;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static AdminAuditLog create(
            Long adminUserId,
            String action,
            String targetType,
            Long targetId,
            String detail,
            String ipAddress,
            String userAgent
    ) {
        AdminAuditLog log = new AdminAuditLog();
        log.adminUserId = adminUserId;
        log.action = action;
        log.targetType = targetType;
        log.targetId = targetId;
        log.detail = detail;
        log.ipAddress = truncate(ipAddress, 64);
        log.userAgent = truncate(userAgent, 512);
        log.createdAt = Instant.now();
        return log;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public Long getId() {
        return id;
    }

    public Long getAdminUserId() {
        return adminUserId;
    }

    public String getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public String getDetail() {
        return detail;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
