package com.evirag.chat.dto;

import com.evirag.chat.ChatSession;
import java.time.Instant;

/**
 * 聊天会话响应。
 */
public record ChatSessionResponse(
        // 会话主键，发送消息时会放到 URL 路径中。
        Long id,
        // 为空表示自由会话；有值表示该会话绑定某个知识库。
        Long knowledgeBaseId,
        // 会话标题，目前由创建请求传入或使用默认标题。
        String title,
        // 创建时间用于展示历史会话。
        Instant createdAt,
        // 更新时间会在发送新消息后刷新，用于会话列表排序。
        Instant updatedAt
) {

    /**
     * 把 ChatSession 实体转换成列表和详情接口都能复用的响应结构。
     */
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
