package com.evirag.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.evirag.document.parser.DocxDocumentParser;
import com.evirag.document.parser.MarkdownDocumentParser;
import com.evirag.document.parser.PdfDocumentParser;
import com.evirag.document.parser.TxtDocumentParser;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;

/**
 * 文档解析器测试。
 *
 * <p>TXT 和 Markdown 使用 resources/samples 中的明文样例；DOCX 与 PDF 的内容同样来自样例文本，
 * 测试时生成标准二进制文件，避免把难以审阅的二进制样例提交到仓库。</p>
 */
class DocumentParserTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesTxtAsPlainTextWithParagraphPositions() throws Exception {
        Path sample = new ClassPathResource("samples/sample.txt").getFile().toPath();

        ParsedDocument parsed = new TxtDocumentParser().parse(sample, "sample.txt");

        assertThat(parsed.success()).isTrue();
        assertThat(parsed.text()).contains("EviRAG 文本样例");
        assertThat(parsed.positions()).extracting(ParsedDocument.Position::label)
                .contains("paragraph-1", "paragraph-2");
    }

    @Test
    void parsesMarkdownHeadingsAndText() throws Exception {
        Path sample = new ClassPathResource("samples/sample.md").getFile().toPath();

        ParsedDocument parsed = new MarkdownDocumentParser().parse(sample, "sample.md");

        assertThat(parsed.success()).isTrue();
        assertThat(parsed.text()).contains("Markdown 样例");
        assertThat(parsed.titles()).containsExactly("Markdown 样例", "证据列表");
    }

    @Test
    void parsesDocxParagraphsWithApachePoi() throws Exception {
        Path sample = tempDir.resolve("sample.docx");
        writeDocx(sample, "DOCX 样例标题", "第一段证据内容", "第二段处理意见");

        ParsedDocument parsed = new DocxDocumentParser().parse(sample, "sample.docx");

        assertThat(parsed.success()).isTrue();
        assertThat(parsed.text()).contains("第一段证据内容", "第二段处理意见");
        assertThat(parsed.positions()).extracting(ParsedDocument.Position::label)
                .contains("paragraph-1", "paragraph-2", "paragraph-3");
    }

    @Test
    void parsesPdfPagesWithPdfBox() throws Exception {
        Path sample = tempDir.resolve("sample.pdf");
        writePdf(sample, "PDF sample title", "first page evidence text");

        ParsedDocument parsed = new PdfDocumentParser().parse(sample, "sample.pdf");

        assertThat(parsed.success()).isTrue();
        assertThat(parsed.text()).contains("PDF sample title", "first page evidence text");
        assertThat(parsed.positions()).extracting(ParsedDocument.Position::label).contains("page-1");
    }

    @Test
    void returnsFailedStageAndRawSummaryWhenParsingFails() throws Exception {
        Path broken = tempDir.resolve("broken.pdf");
        Files.writeString(broken, "this is not a pdf");

        ParsedDocument parsed = new PdfDocumentParser().parse(broken, "broken.pdf");

        assertThat(parsed.success()).isFalse();
        assertThat(parsed.error()).isNotNull();
        assertThat(parsed.error().stage()).isEqualTo("PARSE_PDF");
        assertThat(parsed.error().rawSummary()).isNotBlank();
    }

    private void writeDocx(Path path, String... paragraphs) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             OutputStream outputStream = Files.newOutputStream(path)) {
            for (String text : paragraphs) {
                XWPFParagraph paragraph = document.createParagraph();
                paragraph.createRun().setText(text);
            }
            document.write(outputStream);
        }
    }

    private void writePdf(Path path, String... lines) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA, 12);
                contentStream.newLineAtOffset(72, 720);
                for (String line : lines) {
                    contentStream.showText(line);
                    contentStream.newLineAtOffset(0, -18);
                }
                contentStream.endText();
            }
            document.save(path.toFile());
        }
    }
}
