package com.evirag.rag;

import com.evirag.llm.LlmMessage;
import java.util.List;

/**
 * RAG 流式事件监听器。
 *
 * <p>rag 模块只表达语义事件；chat 模块负责把这些事件转换成 SSE 并保存消息。</p>
 */
public interface RagStreamListener {

    /**
     * 问题改写完成、准备开始检索时触发。
     */
    void onRetrievalStart(String query);

    /**
     * Chroma 检索完成时触发，把引用证据交给 chat 模块继续发送给前端。
     */
    void onRetrievalDone(List<RagCitation> citations);

    /**
     * LLM 请求发出前触发，主要用于过程日志展示 prompt 摘要。
     */
    default void onLlmRequest(List<LlmMessage> messages) {
    }

    /**
     * LLM 每返回一段增量文本时触发。
     */
    void onAnswerDelta(String delta);

    /**
     * 整个 RAG 回答完成时触发，chat 模块会在这里保存 assistant 消息。
     */
    void onAnswerDone(RagResponse response);

    /**
     * LLM 流结束后触发，主要用于过程日志展示最终响应摘要。
     */
    default void onLlmResponse(String answer) {
    }
}
