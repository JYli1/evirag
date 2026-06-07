package com.evirag.knowledge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 知识库实体。
 *
 * <p>知识库是文档、分块和后续向量集合的所有权边界；所有普通用户查询都必须携带 userId，
 * 不能只按 id 查询后再复用给当前用户。</p>
 */
@Entity
@Table(name = "knowledge_bases")
public class KnowledgeBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "chroma_collection", nullable = false, length = 191)
    private String chromaCollection;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static KnowledgeBase create(Long userId, String name, String description, String chromaCollection) {
        Instant now = Instant.now();
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.userId = userId;
        knowledgeBase.name = normalizeName(name);
        knowledgeBase.description = normalizeDescription(description);
        knowledgeBase.chromaCollection = chromaCollection;
        knowledgeBase.status = "ACTIVE";
        knowledgeBase.createdAt = now;
        knowledgeBase.updatedAt = now;
        return knowledgeBase;
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim();
    }

    private static String normalizeDescription(String description) {
        return description == null || description.isBlank() ? null : description.trim();
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

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getChromaCollection() {
        return chromaCollection;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
