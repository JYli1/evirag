package com.evirag.auth;

/**
 * 验证码邮件发送端口。
 *
 * <p>生产实现负责真正发送邮件，测试可以替换为内存实现；服务层不直接依赖 SMTP 细节。</p>
 */
public interface VerificationEmailSender {

    void sendVerificationCode(String email, VerificationPurpose purpose, String code);
}
