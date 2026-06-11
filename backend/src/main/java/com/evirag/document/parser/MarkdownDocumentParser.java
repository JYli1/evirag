package com.evirag.document.parser;

import com.evirag.document.ParsedDocument;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Markdown 文档解析器。
 *
 * <p>当前保留 Markdown 原文作为纯文本，同时抽取 ATX 标题列表；后续分块可以基于标题进行结构化切分。</p>
 */
@Component
public class MarkdownDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String originalFilename) {
        // Markdown 文件只按 .md 扩展名识别。
        return originalFilename != null && originalFilename.toLowerCase().endsWith(".md");
    }

    @Override
    public ParsedDocument parse(Path path, String originalFilename) {
        try {
            // Markdown 保留原文，后续 ChunkService 还能继续识别 # 标题。
            String text = Files.readString(path, StandardCharsets.UTF_8);
            List<String> titles = text.lines()
                    .map(String::trim)
                    .filter(line -> line.startsWith("#"))
                    .map(line -> line.replaceFirst("^#+\\s*", "").trim())
                    .filter(title -> !title.isBlank())
                    .toList();
            return ParsedDocument.success(text, titles, TxtDocumentParser.paragraphPositions(text));
        } catch (Exception ex) {
            return ParsedDocument.failed("PARSE_MARKDOWN", ex);
        }
    }
}
