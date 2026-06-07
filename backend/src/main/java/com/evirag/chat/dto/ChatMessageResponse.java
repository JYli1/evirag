package com.evirag.chat.dto;

import com.evirag.chat.ChatMessage;
import java.time.Instant;

/**
 * 聊天消息响应。
 */
public record ChatMessageResponse(
        Long id,
        String role,
        String content,
        String citations,
        Boolean lowConfidence,
        Instant createdAt
) {

    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getCitations(),
                message.getLowConfidence(),
                message.getCreatedAt()
        );
    }
}
