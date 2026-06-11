package com.evirag.rag;

import java.util.List;

/**
 * RAG 非流式回答结果。
 *
 * <p>SSE 场景下最终也会复用其中的字段保存完整 assistant 消息和引用证据。</p>
 */
public record RagResponse(
        // 最终回答文本。
        String answer,
        // 改写后的检索问题；如果未改写，通常等于原问题。
        String rewrittenQuery,
        // 本次回答引用的证据片段。
        List<RagCitation> citations,
        // true 表示召回证据整体相关性偏低，前端可提示用户谨慎看待。
        boolean lowConfidence
) {
}
