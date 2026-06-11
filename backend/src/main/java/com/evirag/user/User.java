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

    // 目前 username 默认等于邮箱，保留该字段是为了后续扩展昵称或个人资料。
    @Column(nullable = false, length = 64)
    private String username;

    // 邮箱是登录账号，创建和登录前都会统一转成小写。
    @Column(nullable = false, length = 255)
    private String email;

    // 永远不要保存明文密码；这里保存 BCrypt 哈希。
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    // 角色控制权限，Spring Security 会转换成 ROLE_USER / ROLE_ADMIN。
    @Column(nullable = false, length = 32)
    private String role;

    // 状态控制账号是否能登录，管理员禁用用户时会修改它。
    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * 注册用户的工厂方法。
     *
     * <p>实体构造集中在这里，避免 AuthService 到处手动填默认角色、状态和时间。</p>
     */
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

    /**
     * Spring Security 过滤器会用该方法判断 token 对应用户是否仍然有效。
     */
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    private static String normalizeEmail(String email) {
        // 邮箱大小写不应该影响账号唯一性，因此入库前统一小写。
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

    /**
     * 重置密码时只替换哈希，并刷新更新时间。
     */
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
