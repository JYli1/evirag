package com.evirag.auth;

/**
 * 邮箱验证码用途。
 *
 * <p>同一邮箱在不同用途下互不复用验证码，避免注册验证码被拿去重置密码等跨场景误用。</p>
 */
public enum VerificationPurpose {
    REGISTER,
    PASSWORD_RESET
}
