package com.evirag.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/**
 * 邮箱验证码仓储。
 *
 * <p>查询总是按同一邮箱、同一用途、未消费记录查最新一条，服务层再判断是否过期和失败次数。</p>
 */
public interface EmailVerificationCodeRepository extends JpaRepository<EmailVerificationCode, Long> {

    // Spring Data 根据方法名生成查询：找同邮箱、同用途、未消费的最新一条。
    Optional<EmailVerificationCode> findFirstByEmailAndPurposeAndConsumedFalseOrderByCreatedAtDesc(
            String email,
            VerificationPurpose purpose
    );

    /**
     * 给服务层一个更贴近业务语义的方法名。
     *
     * <p>真正是否可用还要看是否过期、失败次数是否过多，这些判断放在 EmailVerificationService。</p>
     */
    default Optional<EmailVerificationCode> findLatestUsable(String email, VerificationPurpose purpose) {
        return findFirstByEmailAndPurposeAndConsumedFalseOrderByCreatedAtDesc(email, purpose);
    }
}
