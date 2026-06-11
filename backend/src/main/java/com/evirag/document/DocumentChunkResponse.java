package com.evirag.document;

import java.time.Instant;

/**
 * 文档切片展示响应。
 *
 * <p>该响应用于前端做切片可视化预览，content 来自 MySQL 主数据，不从 Chroma 反查。</p>
 */
public record DocumentChunkResponse(
        // 切片主键。
        Long id,
        // 所属文档。
        Long documentId,
        // 所属知识库。
        Long knowledgeBaseId,
        // 切片在文档中的顺序，从 0 开始。
        Integer chunkIndex,
        // 切片正文，前端预览和证据展示都依赖它。
        String content,
        // 文档标题或解析器推断出的来源标题。
        String sourceTitle,
        // 页码、段落号等来源位置。
        String sourceLocation,
        // 粗略 token 估算，管理端统计会用到。
        Integer tokenCount,
        // JSON 元数据，保留给调试和后续扩展。
        String metadata,
        // 切片保存时间。
        Instant createdAt
) {

    /**
     * 切片预览只从 MySQL 读取，避免为了展示再去查 Chroma。
     */
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
