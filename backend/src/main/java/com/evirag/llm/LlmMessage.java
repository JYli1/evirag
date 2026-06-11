package com.evirag.llm;

/**
 * LLM 聊天消息。
 *
 * <p>字段名和 OpenAI Chat Completions 的 message 结构保持一致，便于客户端直接序列化。</p>
 */
public record LlmMessage(
        // OpenAI-compatible chat message role，例如 system/user/assistant。
        String role,
        // 该角色对应的文本内容。
        String content
) {

    /**
     * system 消息用于放规则和身份设定。
     */
    public static LlmMessage system(String content) {
        return new LlmMessage("system", content);
    }

    /**
     * user 消息用于放用户问题或带引用片段的 prompt。
     */
    public static LlmMessage user(String content) {
        return new LlmMessage("user", content);
    }

    /**
     * assistant 消息用于放历史助手回答。
     */
    public static LlmMessage assistant(String content) {
        return new LlmMessage("assistant", content);
    }
}
