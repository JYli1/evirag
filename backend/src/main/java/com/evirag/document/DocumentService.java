package com.evirag.document;

import com.evirag.config.AppProperties;
import com.evirag.knowledge.KnowledgeBaseRepository;
import com.evirag.knowledge.KnowledgeBaseNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档上传与元数据服务。
 *
 * <p>当前实现采用同步保存原始文件并创建 PROCESSING 记录；不在上传阶段执行 embedding 或 Chroma 写入，
 * 后续 Task 5 可消费 PROCESSING 文档进入解析、分块和索引流程。</p>
 */
@Service
public class DocumentService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "txt", "docx", "md");

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentRepository documentRepository;
    private final AppProperties appProperties;

    public DocumentService(
            KnowledgeBaseRepository knowledgeBaseRepository,
            DocumentRepository documentRepository,
            AppProperties appProperties
    ) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentRepository = documentRepository;
        this.appProperties = appProperties;
    }

    @Transactional
    public DocumentResponse upload(Long userId, Long knowledgeBaseId, MultipartFile file) {
        validateFile(file);
        knowledgeBaseRepository.findByIdAndUserId(knowledgeBaseId, userId)
                .orElseThrow(KnowledgeBaseNotFoundException::new);
        try {
            String originalFilename = safeOriginalFilename(file.getOriginalFilename());
            String extension = extensionOf(originalFilename);
            Path target = storagePath(userId, knowledgeBaseId, extension);
            Files.createDirectories(target.getParent());
            file.transferTo(target);
            String sha256 = sha256(target);
            Document document = Document.processing(
                    knowledgeBaseId,
                    userId,
                    originalFilename,
                    target.toAbsolutePath().normalize().toString(),
                    normalizeContentType(file.getContentType()),
                    file.getSize(),
                    sha256
            );
            return DocumentResponse.from(documentRepository.save(document));
        } catch (DocumentUploadException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new DocumentUploadException("文件保存失败：" + sanitize(ex), ex);
        }
    }

    @Transactional(readOnly = true)
    public DocumentResponse getById(Long userId, Long documentId) {
        return documentRepository.findByIdAndUserId(documentId, userId)
                .map(DocumentResponse::from)
                .orElseThrow(DocumentNotFoundException::new);
    }

    /**
     * 查询当前用户在指定知识库下的文档列表。
     *
     * <p>先验证知识库归属再查询文档，避免“无权限知识库”和“空知识库”在接口表现上混在一起。</p>
     */
    @Transactional(readOnly = true)
    public List<DocumentResponse> listByKnowledgeBase(Long userId, Long knowledgeBaseId) {
        knowledgeBaseRepository.findByIdAndUserId(knowledgeBaseId, userId)
                .orElseThrow(KnowledgeBaseNotFoundException::new);
        return documentRepository.findByKnowledgeBaseIdAndUserIdOrderByCreatedAtDesc(knowledgeBaseId, userId)
                .stream()
                .map(DocumentResponse::from)
                .toList();
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new DocumentUploadException("上传文件不能为空");
        }
        long maxBytes = appProperties.getMaxFileSizeMb() * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new DocumentUploadException("文件大小不能超过 " + appProperties.getMaxFileSizeMb() + "MB");
        }
        String extension = extensionOf(safeOriginalFilename(file.getOriginalFilename()));
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new DocumentUploadException("仅支持 PDF、TXT、DOCX、MD 文件");
        }
    }

    private Path storagePath(Long userId, Long knowledgeBaseId, String extension) {
        Path uploadRoot = Path.of(appProperties.getUploadDir()).toAbsolutePath().normalize();
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        return uploadRoot.resolve("user-" + userId).resolve("kb-" + knowledgeBaseId).resolve(storedName).normalize();
    }

    private String safeOriginalFilename(String originalFilename) {
        String cleaned = StringUtils.cleanPath(originalFilename == null ? "" : originalFilename);
        String fileName = Path.of(cleaned).getFileName().toString();
        if (fileName.isBlank() || fileName.contains("..")) {
            throw new DocumentUploadException("原始文件名无效");
        }
        return fileName;
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream inputStream = Files.newInputStream(path);
             DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
            digestInputStream.transferTo(OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private String normalizeContentType(String contentType) {
        return contentType == null || contentType.isBlank() ? null : contentType;
    }

    private String sanitize(Exception exception) {
        return (exception.getClass().getSimpleName() + ": " + exception.getMessage())
                .replaceAll("(?i)(api[_-]?key|secret|token|password)=\\S+", "$1=***");
    }
}
