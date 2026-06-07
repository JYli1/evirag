package com.evirag.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 发送消息请求。
 */
public record SendMessageRequest(
        @NotBlank(message = "问题不能为空")
        @Size(max = 8000, message = "问题不能超过 8000 个字符")
        String content
) {
}
