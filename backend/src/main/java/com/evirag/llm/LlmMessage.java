package com.evirag.llm;

/**
 * LLM 聊天消息。
 *
 * <p>字段名和 OpenAI Chat Completions 的 message 结构保持一致，便于客户端直接序列化。</p>
 */
public record LlmMessage(String role, String content) {

    public static LlmMessage system(String content) {
        return new LlmMessage("system", content);
    }

    public static LlmMessage user(String content) {
        return new LlmMessage("user", content);
    }

    public static LlmMessage assistant(String content) {
        return new LlmMessage("assistant", content);
    }
}
