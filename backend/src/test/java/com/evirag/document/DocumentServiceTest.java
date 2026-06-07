package com.evirag.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.evirag.config.AppProperties;
import com.evirag.knowledge.KnowledgeBase;
import com.evirag.knowledge.KnowledgeBaseRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 文档上传服务测试。
 *
 * <p>上传阶段只负责校验、保存原始文件和创建 PROCESSING 记录，不做向量化写入；
 * 后续 Task 5 可以在该状态基础上接入索引流程。</p>
 */
class DocumentServiceTest {

    @TempDir
    Path uploadDir;

    private KnowledgeBaseRepository knowledgeBaseRepository;
    private DocumentRepository documentRepository;
    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        knowledgeBaseRepository = org.mockito.Mockito.mock(KnowledgeBaseRepository.class);
        documentRepository = org.mockito.Mockito.mock(DocumentRepository.class);
        AppProperties appProperties = new AppProperties();
        appProperties.setUploadDir(uploadDir.toString());
        appProperties.setMaxFileSizeMb(20);
        documentService = new DocumentService(knowledgeBaseRepository, documentRepository, appProperties);
    }

    @Test
    void uploadStoresFileAndCreatesProcessingRecord() throws Exception {
        Long userId = 7L;
        Long knowledgeBaseId = 9L;
        KnowledgeBase knowledgeBase = KnowledgeBase.create(userId, "合同库", "说明", "rag_kb_7_contract");
        knowledgeBase.setId(knowledgeBaseId);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "..\\contract.txt",
                "text/plain",
                "合同正文".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        when(knowledgeBaseRepository.findByIdAndUserId(knowledgeBaseId, userId)).thenReturn(Optional.of(knowledgeBase));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> {
            Document document = invocation.getArgument(0);
            document.setId(100L);
            return document;
        });

        DocumentResponse response = documentService.upload(userId, knowledgeBaseId, file);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.parseStatus()).isEqualTo(DocumentStatus.PROCESSING);
        assertThat(response.originalFilename()).isEqualTo("contract.txt");
        assertThat(response.storedPath()).startsWith(uploadDir.toAbsolutePath().normalize().toString());
        assertThat(Files.exists(Path.of(response.storedPath()))).isTrue();
    }

    @Test
    void rejectsUnsupportedExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "payload.exe",
                "application/octet-stream",
                new byte[] {1, 2, 3}
        );

        assertThatThrownBy(() -> documentService.upload(1L, 1L, file))
                .isInstanceOf(DocumentUploadException.class)
                .hasMessageContaining("仅支持 PDF、TXT、DOCX、MD 文件");
    }

    @Test
    void rejectsFileLargerThanConfiguredLimit() {
        AppProperties appProperties = new AppProperties();
        appProperties.setUploadDir(uploadDir.toString());
        appProperties.setMaxFileSizeMb(1);
        DocumentService limitedService = new DocumentService(knowledgeBaseRepository, documentRepository, appProperties);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.txt",
                "text/plain",
                new byte[1024 * 1024 + 1]
        );

        assertThatThrownBy(() -> limitedService.upload(1L, 1L, file))
                .isInstanceOf(DocumentUploadException.class)
                .hasMessageContaining("文件大小不能超过 1MB");
    }
}
