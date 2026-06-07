package com.evirag.auth;

/**
 * 认证业务异常。
 *
 * <p>登录失败统一使用通用文案，避免通过响应消息区分邮箱不存在、密码错误或账号禁用。</p>
 */
public class AuthException extends RuntimeException {

    public AuthException(String message) {
        super(message);
    }
}
