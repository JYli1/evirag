package com.evirag.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 邮箱验证码持久化记录。
 *
 * <p>实体只保存验证码哈希，不保存明文；明文只在发送邮件时短暂存在于内存中。</p>
 */
@Entity
@Table(name = "email_verification_codes")
public class EmailVerificationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 验证码所属邮箱。
    @Column(nullable = false, length = 255)
    private String email;

    // 只保存验证码哈希，不保存邮件中发送出去的 6 位明文。
    @Column(name = "code_hash", nullable = false, length = 255)
    private String codeHash;

    // REGISTER 和 PASSWORD_RESET 分开，避免跨用途复用验证码。
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private VerificationPurpose purpose;

    // 验证码成功使用后置为 true，防止重复提交。
    @Column(nullable = false)
    private boolean consumed;

    // 过期时间，验证时以服务器时间判断。
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // 发送请求来源 IP，主要用于审计和后续限流。
    @Column(name = "sent_ip", length = 64)
    private String sentIp;

    // 当前记录的发送次数，预留给更细的限流策略。
    @Column(name = "send_count", nullable = false)
    private int sendCount;

    // 验证失败次数，超过阈值后服务层会拒绝继续尝试。
    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    /**
     * 创建新的验证码记录。
     *
     * <p>明文验证码由服务层生成并发送邮件，这里只保存哈希和用途。</p>
     */
    public static EmailVerificationCode create(
            String email,
            VerificationPurpose purpose,
            String codeHash,
            Instant expiresAt,
            String sentIp,
            Instant createdAt
    ) {
        EmailVerificationCode code = new EmailVerificationCode();
        code.email = email;
        code.purpose = purpose;
        code.codeHash = codeHash;
        code.expiresAt = expiresAt;
        code.sentIp = sentIp;
        code.createdAt = createdAt;
        code.sendCount = 1;
        code.failureCount = 0;
        code.consumed = false;
        return code;
    }

    /**
     * 判断验证码是否过期。
     */
    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public VerificationPurpose getPurpose() {
        return purpose;
    }

    public boolean isConsumed() {
        return consumed;
    }

    public void setConsumed(boolean consumed) {
        this.consumed = consumed;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getSentIp() {
        return sentIp;
    }

    public int getSendCount() {
        return sendCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }
}
