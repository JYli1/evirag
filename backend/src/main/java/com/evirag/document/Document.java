package com.evirag.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 上传文档实体。
 *
 * <p>这里只保存原始文件元数据和解析状态；分块、embedding、Chroma 写入属于后续索引任务。</p>
 */
@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 所属知识库，删除知识库或查询知识库文档时使用。
    @Column(name = "knowledge_base_id", nullable = false)
    private Long knowledgeBaseId;

    // 上传用户，用于普通用户数据隔离和管理员统计。
    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 用户上传时的原始文件名，展示时用它而不是 storedPath。
    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    // 服务端本地保存路径，避免直接信任原文件名做路径。
    @Column(name = "stored_path", nullable = false, length = 512)
    private String storedPath;

    // 浏览器上报的 MIME 类型，只作为辅助信息，真正解析仍看扩展名和解析器。
    @Column(name = "content_type", length = 128)
    private String contentType;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    // 文件 SHA-256，后续可用于重复文件检测或审计。
    @Column(nullable = false, length = 64)
    private String sha256;

    // 使用字符串保存枚举，数据库值更直观，也避免 enum 顺序变动造成问题。
    @Enumerated(EnumType.STRING)
    @Column(name = "parse_status", nullable = false, length = 32)
    private DocumentStatus parseStatus;

    // 给用户看的错误文案。
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // 失败阶段，例如 PARSE、CHUNK、EMBEDDING、CHROMA。
    @Column(name = "error_stage", length = 64)
    private String errorStage;

    // 底层错误摘要，经过脱敏后保存，方便调试配置或第三方接口问题。
    @Column(name = "raw_error_summary", columnDefinition = "TEXT")
    private String rawErrorSummary;

    // READY 后的切片数量；PROCESSING/FAILED 时通常为 0。
    @Column(name = "chunk_count", nullable = false)
    private Integer chunkCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * 上传成功但尚未索引完成时创建 Document。
     *
     * <p>先保存 PROCESSING 状态，前端才能立刻看到“处理中”，后续异步任务再更新为 READY 或 FAILED。</p>
     */
    public static Document processing(
            Long knowledgeBaseId,
            Long userId,
            String originalFilename,
            String storedPath,
            String contentType,
            long fileSizeBytes,
            String sha256
    ) {
        Instant now = Instant.now();
        Document document = new Document();
        document.knowledgeBaseId = knowledgeBaseId;
        document.userId = userId;
        document.originalFilename = originalFilename;
        document.storedPath = storedPath;
        document.contentType = contentType;
        document.fileSizeBytes = fileSizeBytes;
        document.sha256 = sha256;
        document.parseStatus = DocumentStatus.PROCESSING;
        document.chunkCount = 0;
        document.createdAt = now;
        document.updatedAt = now;
        return document;
    }

    /**
     * 标记文档索引完成。
     *
     * <p>Task 5 的异步索引流程会在解析、切片、embedding 和 Chroma 入库都完成后调用该方法。</p>
     */
    public void markReady(int chunkCount) {
        this.parseStatus = DocumentStatus.READY;
        this.errorStage = null;
        this.errorMessage = null;
        this.rawErrorSummary = null;
        this.chunkCount = chunkCount;
        this.updatedAt = Instant.now();
    }

    /**
     * 标记文档处理失败，并保留可展示文案和原始错误摘要。
     *
     * <p>原始错误摘要不在这里吞掉，方便前端按浅色调试文本展示真实失败原因。</p>
     */
    public void markFailed(String errorStage, String errorMessage, String rawErrorSummary) {
        this.parseStatus = DocumentStatus.FAILED;
        this.errorStage = errorStage;
        this.errorMessage = errorMessage;
        this.rawErrorSummary = rawErrorSummary;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getStoredPath() {
        return storedPath;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public DocumentStatus getParseStatus() {
        return parseStatus;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getErrorStage() {
        return errorStage;
    }

    public String getRawErrorSummary() {
        return rawErrorSummary;
    }

    public Integer getChunkCount() {
        return chunkCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
