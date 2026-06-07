package com.evirag.rag;

import java.util.List;

/**
 * RAG 流式事件监听器。
 *
 * <p>rag 模块只表达语义事件；chat 模块负责把这些事件转换成 SSE 并保存消息。</p>
 */
public interface RagStreamListener {

    void onRetrievalStart(String query);

    void onRetrievalDone(List<RagCitation> citations);

    void onAnswerDelta(String delta);

    void onAnswerDone(RagResponse response);
}
