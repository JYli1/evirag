package com.evirag.chat.dto;

import com.evirag.chat.ChatMessage;
import java.time.Instant;

/**
 * 聊天消息响应。
 */
public record ChatMessageResponse(
        // 消息主键；前端渲染列表时可作为稳定 key。
        Long id,
        // USER 或 ASSISTANT，决定消息气泡显示在用户侧还是助手侧。
        String role,
        // 消息正文；助手消息可能包含 Markdown。
        String content,
        // JSON 字符串形式的引用证据，保持原始结构方便后续扩展。
        String citations,
        // true 表示引用相关性偏低，前端可展示低置信提示。
        Boolean lowConfidence,
        // 创建时间用于消息排序和展示。
        Instant createdAt
) {

    /**
     * 把数据库消息实体裁剪成前端需要的字段。
     */
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
