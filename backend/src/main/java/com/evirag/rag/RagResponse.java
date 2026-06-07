package com.evirag.rag;

import java.util.List;

/**
 * RAG 非流式回答结果。
 *
 * <p>SSE 场景下最终也会复用其中的字段保存完整 assistant 消息和引用证据。</p>
 */
public record RagResponse(
        String answer,
        String rewrittenQuery,
        List<RagCitation> citations,
        boolean lowConfidence
) {
}
