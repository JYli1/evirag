package com.evirag.document.parser;

import com.evirag.document.ParsedDocument;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * PDF 文档解析器。
 *
 * <p>使用 PDFBox 按页抽取文本，并把每页映射为 page-N 位置；扫描件 OCR 不属于 Task 4 范围。</p>
 */
@Component
public class PdfDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String originalFilename) {
        // PDF 只按扩展名选择解析器；扫描件不会自动 OCR。
        return originalFilename != null && originalFilename.toLowerCase().endsWith(".pdf");
    }

    @Override
    public ParsedDocument parse(Path path, String originalFilename) {
        try (PDDocument document = PDDocument.load(path.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            StringBuilder text = new StringBuilder();
            List<ParsedDocument.Position> positions = new ArrayList<>();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                // PDFTextStripper 可以限制页码范围，这里逐页抽取以保留 page-N 来源。
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(document).trim();
                int start = text.length();
                if (!text.isEmpty()) {
                    // 页与页之间插入换行，避免文本直接粘连。
                    text.append(System.lineSeparator());
                    start = text.length();
                }
                text.append(pageText);
                positions.add(new ParsedDocument.Position("page-" + page, start, text.length()));
            }
            return ParsedDocument.success(text.toString(), List.of(), positions);
        } catch (Exception ex) {
            return ParsedDocument.failed("PARSE_PDF", ex);
        }
    }
}
