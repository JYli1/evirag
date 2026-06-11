package com.evirag.document;

import java.time.Instant;

/**
 * 文档元数据响应。
 *
 * <p>storedPath 目前用于前端调试和详情展示；正式下载接口后可改为受控下载 URL。</p>
 */
public record DocumentResponse(
        // 文档主键，用于查询详情、预览切片和删除文档。
        Long id,
        // 所属知识库。
        Long knowledgeBaseId,
        // 用户上传时看到的文件名。
        String originalFilename,
        // 服务端保存路径，当前用于调试展示，不建议作为公开下载地址。
        String storedPath,
        // 浏览器上传的 MIME 类型。
        String contentType,
        // 文件大小，前端用于展示 KB/MB。
        Long fileSizeBytes,
        // 文件内容哈希，便于后续做重复文件判断。
        String sha256,
        // PROCESSING、READY、FAILED，决定前端显示处理状态。
        DocumentStatus parseStatus,
        // 出错阶段，例如 PARSE、CHUNK、EMBEDDING、CHROMA。
        String errorStage,
        // 给用户看的错误摘要。
        String errorMessage,
        // 更接近底层的脱敏错误摘要，方便调试。
        String rawErrorSummary,
        // 成功索引后的切片数量。
        Integer chunkCount,
        // 上传时间。
        Instant createdAt,
        // 状态更新时间。
        Instant updatedAt
) {

    /**
     * 把 Document 实体裁剪成前端文档列表需要的字段。
     */
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
