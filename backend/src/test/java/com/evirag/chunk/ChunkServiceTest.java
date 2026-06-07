package com.evirag.chunk;

import static org.assertj.core.api.Assertions.assertThat;

import com.evirag.document.ParsedDocument;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 文本切片服务测试。
 *
 * <p>切片是 RAG 入库质量的第一道关口：既要保留标题和顺序，也要保证超长段落按带重叠窗口切开。</p>
 */
class ChunkServiceTest {

    @Test
    void splitsByHeadingParagraphLengthAndOverlapWindow() {
        String longParagraph = "abcdefghijklmnopqrstuvwxyz0123456789";
        String text = "# 合同条款\n\n第一段证据\n\n" + longParagraph;
        ParsedDocument parsedDocument = ParsedDocument.success(
                text,
                List.of("合同条款"),
                List.of(new ParsedDocument.Position("paragraph-1", 0, text.length()))
        );

        ChunkService chunkService = new ChunkService(20, 5);

        List<TextChunk> chunks = chunkService.split(parsedDocument);

        assertThat(chunks).hasSize(4);
        assertThat(chunks).extracting(TextChunk::chunkIndex).containsExactly(0, 1, 2, 3);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.sourceTitle()).isEqualTo("合同条款");
            assertThat(chunk.text()).hasSizeLessThanOrEqualTo(20);
        });
        assertThat(chunks.get(1).text().substring(15))
                .isEqualTo(chunks.get(2).text().substring(0, 5));
        assertThat(chunks.get(2).text().substring(15))
                .isEqualTo(chunks.get(3).text().substring(0, 5));
    }

    @Test
    void returnsEmptyListForBlankParsedText() {
        ChunkService chunkService = new ChunkService(20, 5);

        List<TextChunk> chunks = chunkService.split(ParsedDocument.success("  ", List.of(), List.of()));

        assertThat(chunks).isEmpty();
    }
}
