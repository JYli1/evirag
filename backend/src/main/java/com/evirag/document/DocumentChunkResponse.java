package com.evirag.document;

import java.time.Instant;

/**
 * 文档切片展示响应。
 *
 * <p>该响应用于前端做切片可视化预览，content 来自 MySQL 主数据，不从 Chroma 反查。</p>
 */
public record DocumentChunkResponse(
        Long id,
        Long documentId,
        Long knowledgeBaseId,
        Integer chunkIndex,
        String content,
        String sourceTitle,
        String sourceLocation,
        Integer tokenCount,
        String metadata,
        Instant createdAt
) {

    public static DocumentChunkResponse from(DocumentChunk chunk) {
        return new DocumentChunkResponse(
                chunk.getId(),
                chunk.getDocumentId(),
                chunk.getKnowledgeBaseId(),
                chunk.getChunkIndex(),
                chunk.getContent(),
                chunk.getSourceTitle(),
                chunk.getSourceLocation(),
                chunk.getTokenCount(),
                chunk.getMetadata(),
                chunk.getCreatedAt()
        );
    }
}
