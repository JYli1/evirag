package com.evirag.auth;

import com.evirag.config.AppProperties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * 基于 Spring Mail 的验证码发送实现。
 *
 * <p>如果生产环境没有配置发件人地址，发送前直接失败，避免接口假装发送成功导致用户永远收不到验证码。</p>
 */
@Component
public class SmtpVerificationEmailSender implements VerificationEmailSender {

    private final JavaMailSender mailSender;
    private final AppProperties appProperties;

    public SmtpVerificationEmailSender(JavaMailSender mailSender, AppProperties appProperties) {
        this.mailSender = mailSender;
        this.appProperties = appProperties;
    }

    @Override
    public void sendVerificationCode(String email, VerificationPurpose purpose, String code) {
        String from = appProperties.getMail().getFrom();
        if (from == null || from.isBlank()) {
            // 真实邮件发不出去时直接失败，避免前端误以为验证码已发送。
            throw new VerificationCodeException("邮件发件人未配置");
        }

        // SimpleMailMessage 适合纯文本验证码邮件，避免引入 HTML 模板复杂度。
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject(subjectOf(purpose));
        message.setText("您的 EviRAG 验证码是：" + code + "，5 分钟内有效。");
        mailSender.send(message);
    }

    private String subjectOf(VerificationPurpose purpose) {
        // 根据验证码用途区分邮件标题，用户能看出当前操作场景。
        return purpose == VerificationPurpose.PASSWORD_RESET ? "EviRAG 密码重置验证码" : "EviRAG 注册验证码";
    }
}
