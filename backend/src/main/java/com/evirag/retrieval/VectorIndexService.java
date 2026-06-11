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
        // 只传 documentId，不直接传 Document 对象，是为了异步线程重新从数据库读取最新状态，避免拿到过期实体。
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
            // 索引流程会同时写 MySQL 和 Chroma。MySQL 保存“文档/切片元数据”，
            // Chroma 保存“向量和可检索文本”。两边都成功后，文档状态才会变成 READY。
            KnowledgeBase knowledgeBase = knowledgeBaseRepository
                    .findByIdAndUserId(document.getKnowledgeBaseId(), document.getUserId())
                    .orElseThrow(() -> new IllegalStateException("知识库不存在或文档归属不匹配"));

            ParsedDocument parsedDocument = parse(document);
            if (!parsedDocument.success()) {
                // 解析失败可能是文件损坏、格式不支持、PDF 不能提取文字等，失败信息会显示在文档列表里。
                fail(document, parsedDocument.error().stage(), "文档解析失败", parsedDocument.error().rawSummary());
                return;
            }

            List<TextChunk> textChunks = chunkService.split(parsedDocument);
            if (textChunks.isEmpty()) {
                // 空文档不能进入 Embedding，否则会浪费调用次数，并且 Chroma 里也没有可检索内容。
                fail(document, "CHUNK", "文档解析后没有可索引文本",
                        "Parsed text is blank. 如果这是扫描版 PDF，需要先 OCR；如果是 DOCX，请确认正文或表格中存在可复制文本。");
                return;
            }

            // embedding 服务负责把每个文本切片变成一组数字向量。后续相似度检索比较的就是这些数字向量。
            List<List<Double>> embeddings = embeddingClient.embed(textChunks.stream().map(TextChunk::text).toList());
            if (embeddings.size() != textChunks.size()) {
                // 每个切片必须对应一个向量；数量不一致时无法判断哪个向量属于哪个切片，必须终止。
                fail(document, "EMBEDDING", "Embedding 返回数量不匹配",
                        "expected=" + textChunks.size() + ", actual=" + embeddings.size());
                return;
            }

            // 先确保 collection 存在，再删除同一文档的旧向量，最后写入新向量。
            // 这样重复上传或重新索引时，不会留下过期切片。
            chromaClient.ensureCollection(knowledgeBase.getChromaCollection());
            chromaClient.deleteByDocumentId(knowledgeBase.getChromaCollection(), document.getId());
            List<DocumentChunk> savedChunks = replaceChunks(document, textChunks);
            List<ChromaClient.ChromaVector> vectors = buildVectors(document, savedChunks, embeddings);
            chromaClient.upsert(knowledgeBase.getChromaCollection(), vectors);

            // 到这里说明 MySQL 切片和 Chroma 向量都已经写入成功，文档才算真正可检索。
            document.markReady(savedChunks.size());
            documentRepository.save(document);
            log.info("文档索引完成：documentId={}, chunkCount={}", document.getId(), savedChunks.size());
        } catch (EmbeddingException ex) {
            fail(document, ex.getStage(), "Embedding 调用失败", ex.getRawSummary());
        } catch (ChromaException ex) {
            // Chroma 写入失败时清掉 MySQL 切片，避免页面显示“有切片”，但实际向量库无法检索。
            documentChunkRepository.deleteByDocumentId(document.getId());
            fail(document, ex.getStage(), "Chroma 向量库写入失败", ex.getRawSummary());
        } catch (Exception ex) {
            fail(document, "INDEX", "文档索引失败", sanitize(ex));
        }
    }

    private ParsedDocument parse(Document document) {
        // documentParsers 是 Spring 注入进来的解析器列表。
        // 每个解析器自己判断是否支持当前文件名，例如 PDF 解析器只处理 .pdf。
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
            // vectorId 是 Chroma 里的主键。加入 UUID 可以避免重建索引时旧 id 与新 id 冲突。
            String vectorId = "doc-" + document.getId() + "-chunk-" + textChunk.chunkIndex()
                    + "-" + UUID.randomUUID().toString().replace("-", "");
            // 第一次保存时 chunk_id 还不存在，所以 metadata 先写 null，等数据库生成主键后再回填。
            chunks.add(DocumentChunk.create(document, textChunk, vectorId, metadataJson(document, textChunk, null)));
        }
        List<DocumentChunk> savedChunks = documentChunkRepository.saveAll(chunks);
        for (int i = 0; i < savedChunks.size(); i++) {
            DocumentChunk savedChunk = savedChunks.get(i);
            // 回填 chunk_id 后，前端展示引用来源时可以直接定位到 MySQL 的 document_chunks 记录。
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
            // Chroma 同时保存向量、原文片段和 metadata。查询时返回 metadata，RAG 再把它转换成引用信息。
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
        // metadata 会同时保存进 MySQL 和 Chroma，检索命中后就能知道片段属于哪个用户、知识库、文档和切片。
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
        // 这个 Map 是写给 Chroma 的结构化过滤条件，字段名要和 RagService 查询 where 中使用的字段一致。
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
        // 失败时不删除文档记录，让用户能在页面看到失败原因，也能手动删除这份失败文档。
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
