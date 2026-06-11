package com.evirag.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 邮箱验证码服务。
 *
 * <p>服务负责生成验证码、保存哈希、发送邮件和消费验证。限流、过期和错误次数都在这里集中判断，Controller 不参与安全细节。</p>
 */
@Service
public class EmailVerificationService {

    private static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final int MAX_FAILURES = 5;

    private final EmailVerificationCodeRepository repository;
    private final VerificationEmailSender emailSender;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public EmailVerificationService(
            EmailVerificationCodeRepository repository,
            VerificationEmailSender emailSender,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.repository = repository;
        this.emailSender = emailSender;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /**
     * 发送验证码。60 秒内已有未消费验证码时拒绝重发，避免邮箱轰炸和验证码暴力刷新。
     */
    @Transactional
    public void sendCode(String email, VerificationPurpose purpose, String ipAddress) {
        String normalizedEmail = normalizeEmail(email);
        Instant now = Instant.now(clock);
        repository.findLatestUsable(normalizedEmail, purpose)
                .filter(existing -> !existing.isExpired(now))
                .filter(existing -> Duration.between(existing.getCreatedAt(), now).compareTo(RESEND_INTERVAL) < 0)
                .ifPresent(existing -> {
                    // 60 秒内重复请求不重新发，避免邮箱轰炸。
                    throw new VerificationCodeException("验证码发送过于频繁，请稍后再试");
                });

        String code = generateCode();
        // 明文 code 只用于发送邮件；数据库中保存 BCrypt 哈希。
        EmailVerificationCode record = EmailVerificationCode.create(
                normalizedEmail,
                purpose,
                passwordEncoder.encode(code),
                now.plus(CODE_TTL),
                ipAddress,
                now
        );
        repository.saveAndFlush(record);
        emailSender.sendVerificationCode(normalizedEmail, purpose, code);
    }

    /**
     * 消费验证码。成功后立即标记已消费；失败达到 5 次也标记已消费，防止继续猜测。
     */
    @Transactional
    public void verifyCode(String email, VerificationPurpose purpose, String rawCode) {
        String normalizedEmail = normalizeEmail(email);
        Instant now = Instant.now(clock);
        EmailVerificationCode record = repository.findLatestUsable(normalizedEmail, purpose)
                .orElseThrow(() -> new VerificationCodeException("验证码无效或已过期"));

        if (record.isExpired(now)) {
            // 过期验证码立即消费掉，避免用户反复提交旧验证码。
            record.setConsumed(true);
            repository.save(record);
            throw new VerificationCodeException("验证码无效或已过期");
        }

        if (passwordEncoder.matches(rawCode, record.getCodeHash())) {
            // 验证成功后也消费掉，验证码只能用一次。
            record.setConsumed(true);
            repository.save(record);
            return;
        }

        // 错误次数递增，达到上限后消费掉，防止暴力猜测。
        record.setFailureCount(record.getFailureCount() + 1);
        if (record.getFailureCount() >= MAX_FAILURES) {
            record.setConsumed(true);
        }
        repository.save(record);
        throw new VerificationCodeException("验证码无效或已过期");
    }

    private String generateCode() {
        // SecureRandom 比 Random 更适合生成验证码。
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }

    private String normalizeEmail(String email) {
        // 和 AuthService 保持同样的邮箱规范化规则。
        return email == null ? "" : email.trim().toLowerCase();
    }
}
