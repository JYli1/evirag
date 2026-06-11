package com.evirag.chat.dto;

import jakarta.validation.constraints.Size;

/**
 * 创建会话请求。
 */
public record CreateSessionRequest(
        // 标题允许为空；为空时 ChatSession.create 会补默认值。
        @Size(max = 255, message = "会话标题不能超过 255 个字符")
        String title
) {
}
