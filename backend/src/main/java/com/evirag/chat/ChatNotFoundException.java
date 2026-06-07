package com.evirag.chat;

/**
 * 会话不存在或当前用户无权访问。
 */
public class ChatNotFoundException extends RuntimeException {
    public ChatNotFoundException() {
        super("会话不存在或无权访问");
    }
}
