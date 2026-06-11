package com.evirag.chunk;

/**
 * 文档切片结果。
 *
 * <p>切片只表达文本级元数据，不直接依赖数据库实体；后续索引服务会把它转换为 document_chunks 记录和 Chroma 元数据。</p>
 */
public record TextChunk(
        // 切片序号，用于排序和生成稳定 vectorId。
        int chunkIndex,
        // 切片来源标题，可能来自 Markdown 标题或文件名。
        String sourceTitle,
        // 页码、段落号等位置标签。
        String sourceLocation,
        // 切片正文，后续会写入 MySQL 和 Chroma。
        String text
) {
}
