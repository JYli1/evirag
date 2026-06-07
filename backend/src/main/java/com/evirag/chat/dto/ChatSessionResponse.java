package com.evirag.chat.dto;

import com.evirag.chat.ChatSession;
import java.time.Instant;

/**
 * 聊天会话响应。
 */
public record ChatSessionResponse(
        Long id,
        Long knowledgeBaseId,
        String title,
        Instant createdAt,
        Instant updatedAt
) {

    public static ChatSessionResponse from(ChatSession session) {
        return new ChatSessionResponse(
                session.getId(),
                session.getKnowledgeBaseId(),
                session.getTitle(),
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }
}
