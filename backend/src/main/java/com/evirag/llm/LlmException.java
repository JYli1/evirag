package com.evirag.llm;

/**
 * LLM 调用异常。
 *
 * <p>异常携带固定阶段和脱敏后的原始摘要，便于 SSE error 事件和前端浅色详情展示。</p>
 */
public class LlmException extends RuntimeException {

    private final String stage;
    private final String rawSummary;

    public LlmException(String rawSummary) {
        super(rawSummary);
        this.stage = "LLM";
        this.rawSummary = rawSummary;
    }

    public LlmException(String rawSummary, Throwable cause) {
        super(rawSummary, cause);
        this.stage = "LLM";
        this.rawSummary = rawSummary;
    }

    public String getStage() {
        return stage;
    }

    public String getRawSummary() {
        return rawSummary;
    }
}
