package com.evirag.rag;

import java.util.Map;

/**
 * RAG 引用证据。
 *
 * <p>前端右侧证据面板会展示文本、相似度、来源标题和页码/段落位置；低相似度只标记，不丢弃。</p>
 */
public record RagCitation(
        String vectorId,
        String content,
        double score,
        boolean lowScore,
        Long documentId,
        Long chunkId,
        Integer chunkIndex,
        String sourceTitle,
        String sourceLocation,
        Map<String, Object> metadata
) {
}
