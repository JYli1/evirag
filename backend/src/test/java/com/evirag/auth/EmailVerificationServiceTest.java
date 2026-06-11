package com.evirag.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 邮箱验证码服务安全规则测试。
 *
 * <p>这些用例聚焦验证码生命周期本身，不启动 Spring 容器，避免把 SMTP、数据库连接等外部依赖带进单元测试。
 * 邮件发送使用内存记录器，仓储使用 Mockito，只验证服务是否正确执行限流、过期和失败次数作废规则。</p>
 */
class EmailVerificationServiceTest {

    private static final String EMAIL = "user@example.com";
    private static final String IP = "127.0.0.1";

    private EmailVerificationCodeRepository repository;
    private RecordingEmailSender emailSender;
    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        repository = org.mockito.Mockito.mock(EmailVerificationCodeRepository.class);
        emailSender = new RecordingEmailSender();
        service = new EmailVerificationService(
                repository,
                emailSender,
                new BCryptPasswordEncoder(),
                Clock.fixed(Instant.parse("2026-06-08T08:00:00Z"), ZoneOffset.UTC)
        );
    }

    /**
     * 同一邮箱同一用途在 60 秒内已有未消费验证码时，应拒绝再次发送并且不触发邮件发送。
     */
    @Test
    void rejectsRepeatedSendWithinSixtySeconds() {
        EmailVerificationCode existing = EmailVerificationCode.create(
                EMAIL,
                VerificationPurpose.REGISTER,
                "$2a$10$already-hashed",
                Instant.parse("2026-06-08T08:05:00Z"),
                IP,
                Instant.parse("2026-06-08T07:59:30Z")
        );
        when(repository.findLatestUsable(EMAIL, VerificationPurpose.REGISTER))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.sendCode(EMAIL, VerificationPurpose.REGISTER, IP))
                .isInstanceOf(VerificationCodeException.class)
                .hasMessageContaining("发送过于频繁");

        verify(repository, never()).save(any());
        assertThat(emailSender.sentCode()).isNull();
    }

    /**
     * 新验证码的过期时间必须是创建后 5 分钟，数据库只保存哈希，明文只进入邮件发送器。
     */
    @Test
    void createsCodeThatExpiresAfterFiveMinutesAndStoresOnlyHash() {
        when(repository.findLatestUsable(EMAIL, VerificationPurpose.REGISTER))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(EmailVerificationCode.class)))
                .thenAnswer(invocation -> {
                    assertThat(emailSender.sentCode()).isNull();
                    return invocation.getArgument(0);
                });

        service.sendCode(EMAIL, VerificationPurpose.REGISTER, IP);

        ArgumentCaptor<EmailVerificationCode> captor = ArgumentCaptor.forClass(EmailVerificationCode.class);
        verify(repository).saveAndFlush(captor.capture());
        EmailVerificationCode saved = captor.getValue();
        assertThat(saved.getExpiresAt()).isEqualTo(Instant.parse("2026-06-08T08:05:00Z"));
        assertThat(saved.getCodeHash()).isNotEqualTo(emailSender.sentCode());
        assertThat(new BCryptPasswordEncoder().matches(emailSender.sentCode(), saved.getCodeHash())).isTrue();
    }

    /**
     * 验证码输入错误累计 5 次后必须作废，避免攻击者长期暴力枚举同一个验证码。
     */
    @Test
    void invalidatesCodeAfterFiveWrongAttempts() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        EmailVerificationCode existing = EmailVerificationCode.create(
                EMAIL,
                VerificationPurpose.REGISTER,
                encoder.encode("123456"),
                Instant.parse("2026-06-08T08:05:00Z"),
                IP,
                Instant.parse("2026-06-08T07:59:00Z")
        );
        existing.setFailureCount(4);
        when(repository.findLatestUsable(EMAIL, VerificationPurpose.REGISTER))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.verifyCode(EMAIL, VerificationPurpose.REGISTER, "000000"))
                .isInstanceOf(VerificationCodeException.class)
                .hasMessageContaining("验证码无效");

        assertThat(existing.getFailureCount()).isEqualTo(5);
        assertThat(existing.isConsumed()).isTrue();
        verify(repository).save(existing);
    }

    /**
     * 测试替身：不真正发送邮件，只记录最近一次发送的邮箱、用途和验证码。
     */
    private static class RecordingEmailSender implements VerificationEmailSender {

        private String sentCode;

        @Override
        public void sendVerificationCode(String email, VerificationPurpose purpose, String code) {
            this.sentCode = code;
        }

        String sentCode() {
            return sentCode;
        }
    }
}
