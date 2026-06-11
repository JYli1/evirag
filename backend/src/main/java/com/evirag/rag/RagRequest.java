package com.evirag.rag;

import java.util.List;

/**
 * 一次 RAG 问答请求。
 *
 * <p>chat 模块负责补齐 userId、knowledgeBaseId、Chroma collection 和历史消息；rag 模块据此完成检索和 LLM 调用。</p>
 */
public record RagRequest(
        // 当前用户，用于限制 Chroma 检索范围和保存回答归属。
        Long userId,
        // 当前知识库，用于 metadata where 过滤。
        Long knowledgeBaseId,
        // Chroma collection 名称，由 knowledge 模块创建知识库时生成。
        String chromaCollection,
        // 用户原始问题，RAG 服务可能会先改写再检索。
        String question,
        // 最近几轮历史消息，用于 query rewrite 和 prompt 上下文。
        List<RagHistoryMessage> historyMessages,
        // Chroma 返回候选切片数量。
        int topK,
        // 低相似度阈值，低于它的证据会被标记为 lowScore。
        double lowScoreThreshold,
        // 前端开启搜索时由 ChatService 先调用 Tavily 生成的网页上下文。
        String webSearchContext,
        // 是否至少拿到了可用网页资料。
        boolean webSearchHasResults
) {
}
