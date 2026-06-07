package com.evirag.document.parser;

import com.evirag.document.ParsedDocument;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;

/**
 * DOCX 文档解析器。
 *
 * <p>使用 Apache POI 读取段落文本；标题暂按 Word 段落样式名包含 heading/标题 来识别。</p>
 */
@Component
public class DocxDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String originalFilename) {
        return originalFilename != null && originalFilename.toLowerCase().endsWith(".docx");
    }

    @Override
    public ParsedDocument parse(Path path, String originalFilename) {
        try (InputStream inputStream = Files.newInputStream(path);
             XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder text = new StringBuilder();
            List<String> titles = new ArrayList<>();
            List<ParsedDocument.Position> positions = new ArrayList<>();
            int paragraphIndex = 1;
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String paragraphText = paragraph.getText();
                if (paragraphText == null || paragraphText.isBlank()) {
                    continue;
                }
                int start = text.length();
                if (!text.isEmpty()) {
                    text.append(System.lineSeparator());
                    start = text.length();
                }
                text.append(paragraphText);
                positions.add(new ParsedDocument.Position("paragraph-" + paragraphIndex, start, text.length()));
                paragraphIndex++;
                if (isHeading(paragraph)) {
                    titles.add(paragraphText.trim());
                }
            }
            return ParsedDocument.success(text.toString(), titles, positions);
        } catch (Exception ex) {
            return ParsedDocument.failed("PARSE_DOCX", ex);
        }
    }

    private boolean isHeading(XWPFParagraph paragraph) {
        String style = paragraph.getStyle();
        if (style == null) {
            return false;
        }
        String normalized = style.toLowerCase();
        return normalized.contains("heading") || normalized.contains("标题");
    }
}
