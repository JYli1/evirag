package com.evirag.rag;

/**
 * 传给 RAG 编排层的历史消息。
 *
 * <p>历史由 chat 模块读取并裁剪，rag 模块只消费该 DTO，不直接访问聊天表。</p>
 */
public record RagHistoryMessage(String role, String content) {
}
