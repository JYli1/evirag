package com.evirag.chat.dto;

import jakarta.validation.constraints.Size;

/**
 * 创建会话请求。
 */
public record CreateSessionRequest(
        @Size(max = 255, message = "会话标题不能超过 255 个字符")
        String title
) {
}
