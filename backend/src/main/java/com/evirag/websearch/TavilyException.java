package com.evirag.websearch;

/**
 * Tavily 联网搜索异常。
 *
 * <p>SSE 请求无法走普通 JSON 错误响应，所以异常里保留 stage 和 rawSummary，
 * ChatService 会把它们透传到前端过程日志。</p>
 */
public class TavilyException extends RuntimeException {

    private final String stage;
    private final String rawSummary;

    public TavilyException(String message) {
        this("WEB_SEARCH", message, message, null);
    }

    public TavilyException(String message, Throwable cause) {
        this("WEB_SEARCH", message, message, cause);
    }

    public TavilyException(String stage, String message, String rawSummary, Throwable cause) {
        super(message, cause);
        this.stage = stage;
        this.rawSummary = rawSummary;
    }

    public String getStage() {
        return stage;
    }

    public String getRawSummary() {
        return rawSummary;
    }
}
