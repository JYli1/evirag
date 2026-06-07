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

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "code_hash", nullable = false, length = 255)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private VerificationPurpose purpose;

    @Column(nullable = false)
    private boolean consumed;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_ip", length = 64)
    private String sentIp;

    @Column(name = "send_count", nullable = false)
    private int sendCount;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

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
