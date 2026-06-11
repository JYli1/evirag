package com.evirag.websearch;

/**
 * Tavily 调用过程日志回调。
 */
@FunctionalInterface
public interface WebSearchLogSink {

    void log(String direction, String title, String detail);
}
