package com.evirag.auth;

/**
 * 邮箱验证码用途。
 *
 * <p>同一邮箱在不同用途下互不复用验证码，避免注册验证码被拿去重置密码等跨场景误用。</p>
 */
public enum VerificationPurpose {
    /**
     * 注册新账号时使用，验证邮箱确实属于当前注册人。
     */
    REGISTER,
    /**
     * 忘记密码/重置密码时使用，不能和注册验证码混用。
     */
    PASSWORD_RESET
}
