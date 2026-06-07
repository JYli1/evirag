package com.evirag.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.evirag.chunk.ChunkService;
import com.evirag.document.Document;
import com.evirag.document.DocumentChunk;
import com.evirag.document.DocumentChunkRepository;
import com.evirag.document.DocumentRepository;
import com.evirag.document.DocumentStatus;
import com.evirag.document.ParsedDocument;
import com.evirag.document.parser.DocumentParser;
import com.evirag.embedding.EmbeddingClient;
import com.evirag.embedding.EmbeddingException;
import com.evirag.knowledge.KnowledgeBase;
import com.evirag.knowledge.KnowledgeBaseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 文档向量索引服务测试。
 *
 * <p>这里用 mock 隔离外部 embedding 和 Chroma，验证索引编排是否按顺序更新 MySQL 切片、Chroma 元数据和文档状态。</p>
 */
class VectorIndexServiceTest {

    private DocumentRepository documentRepository;
    private KnowledgeBaseRepository knowledgeBaseRepository;
    private DocumentChunkRepository documentChunkRepository;
    private EmbeddingClient embeddingClient;
    private ChromaClient chromaClient;
    private VectorIndexService vectorIndexService;

    @BeforeEach
    void setUp() {
        documentRepository = org.mockito.Mockito.mock(DocumentRepository.class);
        knowledgeBaseRepository = org.mockito.Mockito.mock(KnowledgeBaseRepository.class);
        documentChunkRepository = org.mockito.Mockito.mock(DocumentChunkRepository.class);
        embeddingClient = org.mockito.Mockito.mock(EmbeddingClient.class);
        chromaClient = org.mockito.Mockito.mock(ChromaClient.class);
        DocumentParser parser = new TestParser();
        vectorIndexService = new VectorIndexService(
                documentRepository,
                knowledgeBaseRepository,
                documentChunkRepository,
                List.of(parser),
                new ChunkService(80, 10),
                embeddingClient,
                chromaClient,
                new ObjectMapper()
        );
    }

    @Test
    void indexesDocumentAndMarksReadyWithChromaMetadata() {
        Document document = processingDocument();
        KnowledgeBase knowledgeBase = KnowledgeBase.create(7L, "合同库", "说明", "kb_collection");
        knowledgeBase.setId(9L);
        when(documentRepository.findById(100L)).thenReturn(Optional.of(document));
        when(knowledgeBaseRepository.findByIdAndUserId(9L, 7L)).thenReturn(Optional.of(knowledgeBase));
        when(embeddingClient.embed(any())).thenReturn(List.of(List.of(0.1, 0.2)));
        when(documentChunkRepository.saveAll(any())).thenAnswer(invocation -> {
            List<DocumentChunk> chunks = invocation.getArgument(0);
            for (int i = 0; i < chunks.size(); i++) {
                if (chunks.get(i).getId() == null) {
                    chunks.get(i).setId(200L + i);
                }
            }
            return chunks;
        });

        vectorIndexService.indexDocument(100L);

        verify(chromaClient).ensureCollection("kb_collection");
        verify(chromaClient).deleteByDocumentId("kb_collection", 100L);
        ArgumentCaptor<List<ChromaClient.ChromaVector>> vectorsCaptor = ArgumentCaptor.forClass(List.class);
        verify(chromaClient).upsert(eq("kb_collection"), vectorsCaptor.capture());
        assertThat(vectorsCaptor.getValue()).hasSize(1);
        assertThat(vectorsCaptor.getValue().get(0).metadata())
                .containsEntry("user_id", 7L)
                .containsEntry("knowledge_base_id", 9L)
                .containsEntry("document_id", 100L)
                .containsEntry("chunk_id", 200L);
        assertThat(document.getParseStatus()).isEqualTo(DocumentStatus.READY);
        assertThat(document.getChunkCount()).isEqualTo(1);
    }

    @Test
    void marksDocumentFailedWhenEmbeddingFails() {
        Document document = processingDocument();
        KnowledgeBase knowledgeBase = KnowledgeBase.create(7L, "合同库", "说明", "kb_collection");
        when(documentRepository.findById(100L)).thenReturn(Optional.of(document));
        when(knowledgeBaseRepository.findByIdAndUserId(9L, 7L)).thenReturn(Optional.of(knowledgeBase));
        when(embeddingClient.embed(any())).thenThrow(new EmbeddingException("HTTP 401: invalid api key"));

        vectorIndexService.indexDocument(100L);

        assertThat(document.getParseStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(document.getErrorStage()).isEqualTo("EMBEDDING");
        assertThat(document.getRawErrorSummary()).contains("HTTP 401");
        verify(documentRepository).save(document);
    }

    private Document processingDocument() {
        Document document = Document.processing(
                9L,
                7L,
                "sample.md",
                Path.of("sample.md").toAbsolutePath().toString(),
                "text/markdown",
                20L,
                "sha256"
        );
        document.setId(100L);
        return document;
    }

    private static class TestParser implements DocumentParser {

        @Override
        public boolean supports(String originalFilename) {
            return originalFilename.endsWith(".md");
        }

        @Override
        public ParsedDocument parse(Path path, String originalFilename) {
            String text = "# 标题\n\n第一段证据\n\n第二段证据";
            return ParsedDocument.success(
                    text,
                    List.of("标题"),
                    List.of(new ParsedDocument.Position("paragraph-1", 0, text.length()))
            );
        }
    }
}
