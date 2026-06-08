package com.evirag.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 聊天会话实体。
 *
 * <p>会话属于某个用户，并可绑定一个知识库；普通用户所有查询都必须同时带 userId，避免跨用户读取。</p>
 */
@Entity
@Table(name = "chat_sessions")
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "knowledge_base_id")
    private Long knowledgeBaseId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static ChatSession create(Long userId, Long knowledgeBaseId, String title) {
        Instant now = Instant.now();
        ChatSession session = new ChatSession();
        session.userId = userId;
        session.knowledgeBaseId = knowledgeBaseId;
        session.title = normalizeTitle(title);
        session.createdAt = now;
        session.updatedAt = now;
        return session;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    private static String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return "新的对话";
        }
        return title.trim();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public String getTitle() {
        return title;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
