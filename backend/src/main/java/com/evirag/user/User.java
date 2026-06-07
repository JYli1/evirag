package com.evirag.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 用户实体。
 *
 * <p>密码字段保存 BCrypt 哈希，角色和状态暂用字符串承载，便于后续 Task 7 扩展管理员能力。</p>
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String username;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 32)
    private String role;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static User create(String email, String passwordHash) {
        Instant now = Instant.now();
        User user = new User();
        user.email = normalizeEmail(email);
        user.username = user.email;
        user.passwordHash = passwordHash;
        user.role = "USER";
        user.status = "ACTIVE";
        user.createdAt = now;
        user.updatedAt = now;
        return user;
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        this.updatedAt = Instant.now();
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 管理端更新用户状态。
     *
     * <p>普通测试工具仍可使用 setStatus 构造用户快照；真实业务路径使用该方法同步更新时间。</p>
     */
    public void changeStatus(String status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
