package com.evirag.retrieval;

/**
 * Chroma 阶段异常。
 *
 * <p>异常只保留脱敏后的原始摘要，避免把 API Key、JWT 或本地敏感路径写入响应。</p>
 */
public class ChromaException extends RuntimeException {

    private final String stage;
    private final String rawSummary;

    /**
     * 创建 Chroma 阶段异常，rawSummary 应该已经脱敏。
     */
    public ChromaException(String rawSummary) {
        super(rawSummary);
        this.stage = "CHROMA";
        this.rawSummary = rawSummary;
    }

    /**
     * 带底层异常的构造方法，方便日志保留调用栈。
     */
    public ChromaException(String rawSummary, Throwable cause) {
        super(rawSummary, cause);
        this.stage = "CHROMA";
        this.rawSummary = rawSummary;
    }

    public String getStage() {
        return stage;
    }

    public String getRawSummary() {
        return rawSummary;
    }
}
