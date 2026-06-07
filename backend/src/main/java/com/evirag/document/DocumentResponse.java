package com.evirag.document;

import java.time.Instant;

/**
 * 文档元数据响应。
 *
 * <p>storedPath 目前用于前端调试和详情展示；正式下载接口后可改为受控下载 URL。</p>
 */
public record DocumentResponse(
        Long id,
        Long knowledgeBaseId,
        String originalFilename,
        String storedPath,
        String contentType,
        Long fileSizeBytes,
        String sha256,
        DocumentStatus parseStatus,
        String errorStage,
        String errorMessage,
        String rawErrorSummary,
        Integer chunkCount,
        Instant createdAt,
        Instant updatedAt
) {

    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getKnowledgeBaseId(),
                document.getOriginalFilename(),
                document.getStoredPath(),
                document.getContentType(),
                document.getFileSizeBytes(),
                document.getSha256(),
                document.getParseStatus(),
                document.getErrorStage(),
                document.getErrorMessage(),
                document.getRawErrorSummary(),
                document.getChunkCount(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}
