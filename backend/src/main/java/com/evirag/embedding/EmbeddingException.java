package com.evirag.embedding;

/**
 * Embedding 阶段异常。
 *
 * <p>异常携带阶段和经过脱敏的原始摘要，索引服务会把这些信息写入 documents，前端再以浅色详情展示。</p>
 */
public class EmbeddingException extends RuntimeException {

    private final String stage;
    private final String rawSummary;

    public EmbeddingException(String rawSummary) {
        super(rawSummary);
        this.stage = "EMBEDDING";
        this.rawSummary = rawSummary;
    }

    public EmbeddingException(String rawSummary, Throwable cause) {
        super(rawSummary, cause);
        this.stage = "EMBEDDING";
        this.rawSummary = rawSummary;
    }

    public String getStage() {
        return stage;
    }

    public String getRawSummary() {
        return rawSummary;
    }
}
