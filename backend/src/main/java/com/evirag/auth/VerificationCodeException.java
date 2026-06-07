package com.evirag.auth;

/**
 * 验证码业务异常。
 *
 * <p>该异常只表达验证码发送、过期、错误次数等业务失败，不承载敏感内部状态。</p>
 */
public class VerificationCodeException extends RuntimeException {

    public VerificationCodeException(String message) {
        super(message);
    }
}
