package com.evirag.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 聊天消息实体。
 *
 * <p>用户问题和助手回答都写入该表；assistant 消息额外保存引用证据 JSON 和低置信度标记。</p>
 */
@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ASSISTANT = "ASSISTANT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 32)
    private String role;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(columnDefinition = "JSON")
    private String citations;

    @Column(name = "low_confidence", nullable = false)
    private Boolean lowConfidence;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static ChatMessage user(Long sessionId, Long userId, String content) {
        return create(sessionId, userId, ROLE_USER, content, null, false);
    }

    public static ChatMessage assistant(Long sessionId, Long userId, String content, String citations, boolean lowConfidence) {
        return create(sessionId, userId, ROLE_ASSISTANT, content, citations, lowConfidence);
    }

    private static ChatMessage create(
            Long sessionId,
            Long userId,
            String role,
            String content,
            String citations,
            boolean lowConfidence
    ) {
        ChatMessage message = new ChatMessage();
        message.sessionId = sessionId;
        message.userId = userId;
        message.role = role;
        message.content = content == null ? "" : content;
        message.citations = citations;
        message.lowConfidence = lowConfidence;
        message.createdAt = Instant.now();
        return message;
    }

    public Long getId() {
        return id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getCitations() {
        return citations;
    }

    public Boolean getLowConfidence() {
        return lowConfidence;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
