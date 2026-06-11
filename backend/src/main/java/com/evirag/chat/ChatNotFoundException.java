package com.evirag.chat;

/**
 * 会话不存在或当前用户无权访问。
 */
public class ChatNotFoundException extends RuntimeException {
    /**
     * 不区分“不存在”和“无权访问”，避免用户通过 sessionId 枚举资源。
     */
    public ChatNotFoundException() {
        super("会话不存在或无权访问");
    }
}
