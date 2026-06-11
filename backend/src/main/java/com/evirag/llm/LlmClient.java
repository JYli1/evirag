package com.evirag.llm;

import java.util.List;
import java.util.function.Consumer;

/**
 * 大语言模型客户端接口。
 *
 * <p>该接口只封装聊天模型调用，不做 embedding、检索、数据库读写或 SSE 事件处理。</p>
 */
public interface LlmClient {

    /**
     * 非流式调用，适合 query rewrite、标题生成等短文本任务。
     */
    String complete(List<LlmMessage> messages);

    /**
     * 流式调用，模型每产生一段增量文本就回调一次。
     *
     * @param messages 已经组装好的 OpenAI-compatible messages
     * @param onDelta 每个增量文本片段的消费函数，通常会转成 SSE answer_delta 事件
     */
    void stream(List<LlmMessage> messages, Consumer<String> onDelta);
}
