package com.evirag.rag;

import java.util.List;

/**
 * 一次 RAG 问答请求。
 *
 * <p>chat 模块负责补齐 userId、knowledgeBaseId、Chroma collection 和历史消息；rag 模块据此完成检索和 LLM 调用。</p>
 */
public record RagRequest(
        Long userId,
        Long knowledgeBaseId,
        String chromaCollection,
        String question,
        List<RagHistoryMessage> historyMessages,
        int topK,
        double lowScoreThreshold
) {
}
