package com.evirag.retrieval;

import com.evirag.chunk.ChunkService;
import com.evirag.chunk.TextChunk;
import com.evirag.document.Document;
import com.evirag.document.DocumentChunk;
import com.evirag.document.DocumentChunkRepository;
import com.evirag.document.DocumentRepository;
import com.evirag.document.ParsedDocument;
import com.evirag.document.parser.DocumentParser;
import com.evirag.embedding.EmbeddingClient;
import com.evirag.embedding.EmbeddingException;
import com.evirag.knowledge.KnowledgeBase;
import com.evirag.knowledge.KnowledgeBaseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 文档向量索引服务。
 *
 * <p>该服务串联 Task 5 的完整入库流程：解析文本、切片、调用 embedding、写 Chroma、写 MySQL 切片元数据，
 * 最后更新文档状态。任意阶段失败都会把阶段和脱敏后的原始摘要写回 documents。</p>
 */
@Service
public class VectorIndexService {

    private static final Logger log = LoggerFactory.getLogger(VectorIndexService.class);

    private final DocumentRepository documentRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final List<DocumentParser> documentParsers;
    private final ChunkService chunkService;
    private final EmbeddingClient embeddingClient;
    private final ChromaClient chromaClient;
    private final ObjectMapper objectMapper;

    public VectorIndexService(
            DocumentRepository documentRepository,
            KnowledgeBaseRepository knowledgeBaseRepository,
            DocumentChunkRepository documentChunkRepository,
            List<DocumentParser> documentParsers,
            ChunkService chunkService,
            EmbeddingClient embeddingClient,
            ChromaClient chromaClient,
            ObjectMapper objectMapper
    ) {
        this.documentRepository = documentRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.documentParsers = documentParsers;
        this.chunkService = chunkService;
        this.embeddingClient = embeddingClient;
        this.chromaClient = chromaClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 上传事务提交后异步执行索引，避免外部模型调用拖慢上传接口。
     */
    @Async("documentIndexTaskExecutor")
    public void indexAsync(Long documentId) {
        indexDocument(documentId);
    }

    /**
     * 同步索引入口，便于单元测试精确验证每个阶段。
     */
    public void indexDocument(Long documentId) {
        Optional<Document> optionalDocument = documentRepository.findById(documentId);
        if (optionalDocument.isEmpty()) {
            log.warn("文档索引跳过：documentId={} 不存在", documentId);
            return;
        }

        Document document = optionalDocument.get();
        try {
            KnowledgeBase knowledgeBase = knowledgeBaseRepository
                    .findByIdAndUserId(document.getKnowledgeBaseId(), document.getUserId())
                    .orElseThrow(() -> new IllegalStateException("知识库不存在或文档归属不匹配"));

            ParsedDocument parsedDocument = parse(document);
            if (!parsedDocument.success()) {
                fail(document, parsedDocument.error().stage(), "文档解析失败", parsedDocument.error().rawSummary());
                return;
            }

            List<TextChunk> textChunks = chunkService.split(parsedDocument);
            if (textChunks.isEmpty()) {
                fail(document, "CHUNK", "文档解析后没有可索引文本",
                        "Parsed text is blank. 如果这是扫描版 PDF，需要先 OCR；如果是 DOCX，请确认正文或表格中存在可复制文本。");
                return;
            }

            List<List<Double>> embeddings = embeddingClient.embed(textChunks.stream().map(TextChunk::text).toList());
            if (embeddings.size() != textChunks.size()) {
                fail(document, "EMBEDDING", "Embedding 返回数量不匹配",
                        "expected=" + textChunks.size() + ", actual=" + embeddings.size());
                return;
            }

            chromaClient.ensureCollection(knowledgeBase.getChromaCollection());
            chromaClient.deleteByDocumentId(knowledgeBase.getChromaCollection(), document.getId());
            List<DocumentChunk> savedChunks = replaceChunks(document, textChunks);
            List<ChromaClient.ChromaVector> vectors = buildVectors(document, savedChunks, embeddings);
            chromaClient.upsert(knowledgeBase.getChromaCollection(), vectors);

            document.markReady(savedChunks.size());
            documentRepository.save(document);
            log.info("文档索引完成：documentId={}, chunkCount={}", document.getId(), savedChunks.size());
        } catch (EmbeddingException ex) {
            fail(document, ex.getStage(), "Embedding 调用失败", ex.getRawSummary());
        } catch (ChromaException ex) {
            documentChunkRepository.deleteByDocumentId(document.getId());
            fail(document, ex.getStage(), "Chroma 向量库写入失败", ex.getRawSummary());
        } catch (Exception ex) {
            fail(document, "INDEX", "文档索引失败", sanitize(ex));
        }
    }

    private ParsedDocument parse(Document document) {
        DocumentParser parser = documentParsers.stream()
                .filter(candidate -> candidate.supports(document.getOriginalFilename()))
                .findFirst()
                .orElse(null);
        if (parser == null) {
            return ParsedDocument.failed(
                    "PARSE",
                    new IllegalArgumentException("不支持的文档格式：" + document.getOriginalFilename())
            );
        }
        return parser.parse(Path.of(document.getStoredPath()), document.getOriginalFilename());
    }

    /**
     * 先清理旧切片再保存新切片，使重新索引时 MySQL 主数据不会出现重复序号。
     */
    private List<DocumentChunk> replaceChunks(Document document, List<TextChunk> textChunks) throws Exception {
        documentChunkRepository.deleteByDocumentId(document.getId());
        List<DocumentChunk> chunks = new ArrayList<>();
        for (TextChunk textChunk : textChunks) {
            String vectorId = "doc-" + document.getId() + "-chunk-" + textChunk.chunkIndex()
                    + "-" + UUID.randomUUID().toString().replace("-", "");
            chunks.add(DocumentChunk.create(document, textChunk, vectorId, metadataJson(document, textChunk, null)));
        }
        List<DocumentChunk> savedChunks = documentChunkRepository.saveAll(chunks);
        for (int i = 0; i < savedChunks.size(); i++) {
            DocumentChunk savedChunk = savedChunks.get(i);
            savedChunk.updateMetadata(metadataJson(document, textChunks.get(i), savedChunk.getId()));
        }
        return documentChunkRepository.saveAll(savedChunks);
    }

    private List<ChromaClient.ChromaVector> buildVectors(
            Document document,
            List<DocumentChunk> savedChunks,
            List<List<Double>> embeddings
    ) throws Exception {
        List<ChromaClient.ChromaVector> vectors = new ArrayList<>();
        for (int i = 0; i < savedChunks.size(); i++) {
            DocumentChunk chunk = savedChunks.get(i);
            Map<String, Object> metadata = metadataMap(document, chunk);
            vectors.add(new ChromaClient.ChromaVector(
                    chunk.getChromaEmbeddingId(),
                    embeddings.get(i),
                    chunk.getContent(),
                    metadata
            ));
        }
        return vectors;
    }

    private String metadataJson(Document document, TextChunk textChunk, Long chunkId) throws Exception {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("user_id", document.getUserId());
        metadata.put("knowledge_base_id", document.getKnowledgeBaseId());
        metadata.put("document_id", document.getId());
        metadata.put("chunk_index", textChunk.chunkIndex());
        if (chunkId != null) {
            metadata.put("chunk_id", chunkId);
        }
        if (textChunk.sourceTitle() != null) {
            metadata.put("source_title", textChunk.sourceTitle());
        }
        if (textChunk.sourceLocation() != null) {
            metadata.put("source_location", textChunk.sourceLocation());
        }
        return objectMapper.writeValueAsString(metadata);
    }

    private Map<String, Object> metadataMap(Document document, DocumentChunk chunk) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("user_id", document.getUserId());
        metadata.put("knowledge_base_id", document.getKnowledgeBaseId());
        metadata.put("document_id", document.getId());
        metadata.put("chunk_id", chunk.getId());
        metadata.put("chunk_index", chunk.getChunkIndex());
        if (chunk.getSourceTitle() != null) {
            metadata.put("source_title", chunk.getSourceTitle());
        }
        if (chunk.getSourceLocation() != null) {
            metadata.put("source_location", chunk.getSourceLocation());
        }
        return metadata;
    }

    private void fail(Document document, String stage, String userMessage, String rawSummary) {
        document.markFailed(stage, userMessage, rawSummary);
        documentRepository.save(document);
        log.warn("文档索引失败：documentId={}, stage={}, rawSummary={}",
                document.getId(), stage, rawSummary);
    }

    private String sanitize(Exception exception) {
        String raw = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        return raw.replaceAll("(?i)(api[_-]?key|secret|token|password|authorization)=\\S+", "$1=***");
    }
}
