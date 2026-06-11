package com.evirag.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 发送消息请求。
 */
public record SendMessageRequest(
        // 用户问题会进入 prompt，限制长度可以避免单次请求过大拖垮 LLM 或日志面板。
        @NotBlank(message = "问题不能为空")
        @Size(max = 8000, message = "问题不能超过 8000 个字符")
        String content,
        // true 表示本次问题开启 Tavily 联网搜索；未传时按 false 处理。
        Boolean webSearchEnabled
) {
}
