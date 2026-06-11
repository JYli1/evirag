package com.evirag.auth;

/**
 * 验证码邮件发送端口。
 *
 * <p>生产实现负责真正发送邮件，测试可以替换为内存实现；服务层不直接依赖 SMTP 细节。</p>
 */
public interface VerificationEmailSender {

    /**
     * 发送验证码。
     *
     * <p>接口只描述“要发什么”，SMTP 地址、端口和授权码由具体实现读取配置。</p>
     */
    void sendVerificationCode(String email, VerificationPurpose purpose, String code);
}
