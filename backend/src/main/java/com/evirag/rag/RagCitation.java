package com.evirag.rag;

import java.util.Map;

/**
 * RAG 引用证据。
 *
 * <p>前端右侧证据面板会展示文本、相似度、来源标题和页码/段落位置；低相似度只标记，不丢弃。</p>
 */
public record RagCitation(
        // Chroma 向量记录 ID。
        String vectorId,
        // 被召回的切片正文。
        String content,
        // 相似度分数，当前由 Chroma distance 转换而来。
        double score,
        // 是否低于 lowScoreThreshold。
        boolean lowScore,
        // 来源文档 ID。
        Long documentId,
        // 来源切片 ID。
        Long chunkId,
        // 来源切片序号。
        Integer chunkIndex,
        // 来源标题。
        String sourceTitle,
        // 页码、段落号等来源位置。
        String sourceLocation,
        // 原始 metadata，保留扩展字段。
        Map<String, Object> metadata
) {
}
