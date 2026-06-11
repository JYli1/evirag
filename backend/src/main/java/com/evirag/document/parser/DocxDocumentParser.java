package com.evirag.document.parser;

import com.evirag.document.ParsedDocument;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.openxml4j.exceptions.OLE2NotOfficeXmlFileException;
import org.apache.poi.xwpf.usermodel.BodyElementType;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

/**
 * Word 文档解析器。
 *
 * <p>优先按真正的 DOCX，也就是 OOXML 格式读取；如果文件扩展名是 .docx 但内容实际是老式 OLE2 `.doc`，
 * Apache POI 会抛出 OLE2NotOfficeXmlFileException，此时自动切换到 HWPF 解析老 Word 格式。</p>
 */
@Component
public class DocxDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String originalFilename) {
        // 同时支持标准 .docx 和老式 .doc；也兼容“扩展名写成 .docx，但内容实际是 .doc”的情况。
        if (originalFilename == null) {
            return false;
        }
        String lower = originalFilename.toLowerCase();
        return lower.endsWith(".docx") || lower.endsWith(".doc");
    }

    @Override
    public ParsedDocument parse(Path path, String originalFilename) {
        try {
            return parseDocx(path);
        } catch (OLE2NotOfficeXmlFileException ex) {
            // 典型场景：用户手里的文件名是 .docx，但实际内容是 Word 97-2003 的 OLE2 `.doc`。
            return parseLegacyDoc(path);
        } catch (Exception ex) {
            return ParsedDocument.failed("PARSE_DOCX", ex);
        }
    }

    private ParsedDocument parseDocx(Path path) throws Exception {
        try (InputStream inputStream = Files.newInputStream(path);
             XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder text = new StringBuilder();
            List<String> titles = new ArrayList<>();
            List<ParsedDocument.Position> positions = new ArrayList<>();
            int blockIndex = 1;
            for (IBodyElement element : document.getBodyElements()) {
                if (element.getElementType() == BodyElementType.PARAGRAPH) {
                    // 普通段落按 paragraph-N 记录位置。
                    blockIndex = appendParagraph((XWPFParagraph) element, text, titles, positions, blockIndex);
                }
                if (element.getElementType() == BodyElementType.TABLE) {
                    // 表格转成带分隔符的纯文本行，保证也能被检索。
                    blockIndex = appendTable((XWPFTable) element, text, positions, blockIndex);
                }
            }
            return ParsedDocument.success(text.toString(), titles, positions);
        }
    }

    private ParsedDocument parseLegacyDoc(Path path) {
        try (InputStream inputStream = Files.newInputStream(path);
             HWPFDocument document = new HWPFDocument(inputStream);
             WordExtractor extractor = new WordExtractor(document)) {
            StringBuilder text = new StringBuilder();
            List<ParsedDocument.Position> positions = new ArrayList<>();
            int blockIndex = 1;
            for (String paragraph : extractor.getParagraphText()) {
                if (paragraph == null || paragraph.isBlank()) {
                    continue;
                }
                String normalized = paragraph.trim().replaceAll("\\R+", " ");
                int start = appendBlock(text, normalized);
                positions.add(new ParsedDocument.Position("paragraph-" + blockIndex, start, text.length()));
                blockIndex++;
            }
            return ParsedDocument.success(text.toString(), List.of(), positions);
        } catch (Exception ex) {
            return ParsedDocument.failed("PARSE_DOC", ex);
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
            // 标题列表后续可用于切片来源展示。
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
                    // 单元格内部换行折叠成空格，避免表格行结构被打散。
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
            // 块之间保留换行，避免段落和表格文本粘连。
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
        // 兼容英文 Word 样式 heading 和中文样式“标题”。
        return normalized.contains("heading") || normalized.contains("标题");
    }
}
