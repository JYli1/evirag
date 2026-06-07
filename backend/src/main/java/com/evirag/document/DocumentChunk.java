package com.evirag.document;

import com.evirag.chunk.TextChunk;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 文档切片元数据实体。
 *
 * <p>MySQL 保存业务主数据和可展示文本，Chroma 只保存向量检索索引；两者通过 chromaEmbeddingId 和 chunk_id 互相定位。</p>
 */
@Entity
@Table(name = "document_chunks")
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "knowledge_base_id", nullable = false)
    private Long knowledgeBaseId;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "source_title")
    private String sourceTitle;

    @Column(name = "source_location", length = 128)
    private String sourceLocation;

    @Column(name = "token_count")
    private Integer tokenCount;

    @Column(name = "chroma_embedding_id", nullable = false, length = 191)
    private String chromaEmbeddingId;

    @Column(columnDefinition = "JSON")
    private String metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static DocumentChunk create(
            Document document,
            TextChunk textChunk,
            String chromaEmbeddingId,
            String metadata
    ) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.documentId = document.getId();
        chunk.knowledgeBaseId = document.getKnowledgeBaseId();
        chunk.chunkIndex = textChunk.chunkIndex();
        chunk.content = textChunk.text();
        chunk.sourceTitle = textChunk.sourceTitle();
        chunk.sourceLocation = textChunk.sourceLocation();
        chunk.tokenCount = estimateTokenCount(textChunk.text());
        chunk.chromaEmbeddingId = chromaEmbeddingId;
        chunk.metadata = metadata;
        chunk.createdAt = Instant.now();
        return chunk;
    }

    /**
     * 更新切片元数据 JSON。
     *
     * <p>chunk_id 只有数据库生成主键后才能确定，因此索引服务会在首次保存后回填完整元数据。</p>
     */
    public void updateMetadata(String metadata) {
        this.metadata = metadata;
    }

    private static int estimateTokenCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(text.length() / 4.0));
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public String getSourceTitle() {
        return sourceTitle;
    }

    public String getSourceLocation() {
        return sourceLocation;
    }

    public Integer getTokenCount() {
        return tokenCount;
    }

    public String getChromaEmbeddingId() {
        return chromaEmbeddingId;
    }

    public String getMetadata() {
        return metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
