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

    // 执行动作的管理员 ID。
    @Column(name = "admin_user_id", nullable = false)
    private Long adminUserId;

    // 动作名称，例如 UPDATE_USER_STATUS。
    @Column(nullable = false, length = 128)
    private String action;

    // 被操作对象类型，例如 USER。
    @Column(name = "target_type", nullable = false, length = 64)
    private String targetType;

    // 被操作对象 ID，系统级动作可以为空。
    @Column(name = "target_id")
    private Long targetId;

    // JSON 详情，记录状态变化等业务字段。
    @Column(columnDefinition = "JSON")
    private String detail;

    // 管理员请求来源 IP。
    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    // 浏览器 User-Agent，可能很长，保存前会裁剪。
    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * 创建审计日志。
     *
     * <p>高权限操作建议统一从这个方法创建，确保 IP、User-Agent 和时间都被记录。</p>
     */
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
        // 数据库字段有长度限制，过长的 User-Agent 不应该导致审计日志写入失败。
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
