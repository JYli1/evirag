package com.evirag.websearch;

/**
 * Tavily 联网搜索拼装后的上下文。
 */
public record WebSearchContext(
        // 可直接拼进 LLM prompt 的网页资料。
        String promptText,
        // 是否至少拿到一条可用网页资料。
        boolean hasResults,
        // 是否执行过 Extract。
        boolean usedExtract,
        // Search 返回的结果数量。
        int searchResultCount,
        // Extract 返回的结果数量。
        int extractResultCount
) {

    public static WebSearchContext empty() {
        return new WebSearchContext("", false, false, 0, 0);
    }
}
