package com.evirag.document;

import java.util.List;

/**
 * 统一解析结果。
 *
 * <p>解析器只返回纯文本、标题、页码或段落位置以及失败摘要，不在 Task 4 做文本分块或向量化。</p>
 */
public record ParsedDocument(
        boolean success,
        String text,
        List<String> titles,
        List<Position> positions,
        ParseError error
) {

    public static ParsedDocument success(String text, List<String> titles, List<Position> positions) {
        return new ParsedDocument(true, text, List.copyOf(titles), List.copyOf(positions), null);
    }

    public static ParsedDocument failed(String stage, Exception exception) {
        return new ParsedDocument(false, "", List.of(), List.of(), new ParseError(stage, sanitize(exception)));
    }

    private static String sanitize(Exception exception) {
        String raw = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        return raw.replaceAll("(?i)(api[_-]?key|secret|token|password)=\\S+", "$1=***");
    }

    public record Position(String label, int startOffset, int endOffset) {
    }

    public record ParseError(String stage, String rawSummary) {
    }
}
