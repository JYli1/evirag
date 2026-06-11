package com.evirag.document;

import java.util.List;

/**
 * 统一解析结果。
 *
 * <p>解析器只返回纯文本、标题、页码或段落位置以及失败摘要，不在 Task 4 做文本分块或向量化。</p>
 */
public record ParsedDocument(
        // true 表示解析器成功抽取到文本；false 表示 error 中有失败原因。
        boolean success,
        // 解析出的全文，后续交给 ChunkService 切片。
        String text,
        // 解析器发现的标题列表，目前主要用于切片来源提示。
        List<String> titles,
        // 文本片段和页码/段落位置的对应关系。
        List<Position> positions,
        // 解析失败时的错误阶段和脱敏摘要。
        ParseError error
) {

    /**
     * 成功结果会复制列表，避免调用方之后修改原列表影响解析结果。
     */
    public static ParsedDocument success(String text, List<String> titles, List<Position> positions) {
        return new ParsedDocument(true, text, List.copyOf(titles), List.copyOf(positions), null);
    }

    /**
     * 失败结果统一走这个工厂方法，便于 VectorIndexService 记录错误阶段。
     */
    public static ParsedDocument failed(String stage, Exception exception) {
        return new ParsedDocument(false, "", List.of(), List.of(), new ParseError(stage, sanitize(exception)));
    }

    private static String sanitize(Exception exception) {
        String raw = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        // 错误摘要可能包含连接串或 token，这里做最基础的脱敏。
        return raw.replaceAll("(?i)(api[_-]?key|secret|token|password)=\\S+", "$1=***");
    }

    public record Position(
            // 例如 page-1 或 paragraph-3，给前端和引用证据展示来源位置。
            String label,
            // 该位置在全文中的开始字符下标。
            int startOffset,
            // 该位置在全文中的结束字符下标。
            int endOffset
    ) {
    }

    public record ParseError(
            // 失败阶段。
            String stage,
            // 脱敏后的原始错误摘要。
            String rawSummary
    ) {
    }
}
