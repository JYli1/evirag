package com.evirag.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/**
 * 邮箱验证码仓储。
 *
 * <p>查询总是按同一邮箱、同一用途、未消费记录查最新一条，服务层再判断是否过期和失败次数。</p>
 */
public interface EmailVerificationCodeRepository extends JpaRepository<EmailVerificationCode, Long> {

    Optional<EmailVerificationCode> findFirstByEmailAndPurposeAndConsumedFalseOrderByCreatedAtDesc(
            String email,
            VerificationPurpose purpose
    );

    default Optional<EmailVerificationCode> findLatestUsable(String email, VerificationPurpose purpose) {
        return findFirstByEmailAndPurposeAndConsumedFalseOrderByCreatedAtDesc(email, purpose);
    }
}
