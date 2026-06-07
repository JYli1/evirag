package com.evirag.chunk;

/**
 * 文档切片结果。
 *
 * <p>切片只表达文本级元数据，不直接依赖数据库实体；后续索引服务会把它转换为 document_chunks 记录和 Chroma 元数据。</p>
 */
public record TextChunk(
        int chunkIndex,
        String sourceTitle,
        String sourceLocation,
        String text
) {
}
