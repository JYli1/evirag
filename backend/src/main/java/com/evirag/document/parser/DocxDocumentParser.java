package com.evirag.document.parser;

import com.evirag.document.ParsedDocument;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.xwpf.usermodel.BodyElementType;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
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
            int blockIndex = 1;
            for (IBodyElement element : document.getBodyElements()) {
                if (element.getElementType() == BodyElementType.PARAGRAPH) {
                    blockIndex = appendParagraph((XWPFParagraph) element, text, titles, positions, blockIndex);
                }
                if (element.getElementType() == BodyElementType.TABLE) {
                    blockIndex = appendTable((XWPFTable) element, text, positions, blockIndex);
                }
            }
            return ParsedDocument.success(text.toString(), titles, positions);
        } catch (Exception ex) {
            return ParsedDocument.failed("PARSE_DOCX", ex);
        }
    }

    private int appendParagraph(
            XWPFParagraph paragraph,
            StringBuilder text,
            List<String> titles,
            List<ParsedDocument.Position> positions,
            int blockIndex
    ) {
        String paragraphText = paragraph.getText();
        if (paragraphText == null || paragraphText.isBlank()) {
            return blockIndex;
        }
        int start = appendBlock(text, paragraphText.trim());
        positions.add(new ParsedDocument.Position("paragraph-" + blockIndex, start, text.length()));
        if (isHeading(paragraph)) {
            titles.add(paragraphText.trim());
        }
        return blockIndex + 1;
    }

    private int appendTable(
            XWPFTable table,
            StringBuilder text,
            List<ParsedDocument.Position> positions,
            int blockIndex
    ) {
        List<String> rows = new ArrayList<>();
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                String cellText = cell.getText();
                if (cellText != null && !cellText.isBlank()) {
                    cells.add(cellText.trim().replaceAll("\\R+", " "));
                }
            }
            if (!cells.isEmpty()) {
                rows.add(String.join(" | ", cells));
            }
        }
        if (rows.isEmpty()) {
            return blockIndex;
        }
        int start = appendBlock(text, String.join(System.lineSeparator(), rows));
        positions.add(new ParsedDocument.Position("table-" + blockIndex, start, text.length()));
        return blockIndex + 1;
    }

    private int appendBlock(StringBuilder text, String blockText) {
        int start = text.length();
        if (!text.isEmpty()) {
            text.append(System.lineSeparator());
            start = text.length();
        }
        text.append(blockText);
        return start;
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
