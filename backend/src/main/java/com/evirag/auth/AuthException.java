package com.evirag.auth;

/**
 * 认证业务异常。
 *
 * <p>登录失败统一使用通用文案，避免通过响应消息区分邮箱不存在、密码错误或账号禁用。</p>
 */
public class AuthException extends RuntimeException {

    /**
     * message 会直接进入统一错误响应，所以调用方必须传入可展示且不泄露细节的文案。
     */
    public AuthException(String message) {
        super(message);
    }
}
