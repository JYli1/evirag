package com.evirag.document;

import com.evirag.auth.JwtService.JwtPrincipal;
import com.evirag.common.api.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档 REST 接口。
 *
 * <p>上传和查询都从 JWT 主体读取 userId；前端只提交知识库 ID 和文件，不允许指定上传用户。</p>
 */
@RestController
@RequestMapping("/api")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * 上传文件到指定知识库。
     *
     * <p>接口立即返回 PROCESSING 文档记录，实际解析和向量化在后台异步完成。</p>
     */
    @PostMapping("/knowledge-bases/{knowledgeBaseId}/documents")
    public ApiResponse<DocumentResponse> upload(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long knowledgeBaseId,
            @RequestParam("file") MultipartFile file
    ) {
        return ApiResponse.success(documentService.upload(principal.userId(), knowledgeBaseId, file));
    }

    /**
     * 查询单个文档处理状态和元数据。
     */
    @GetMapping("/documents/{documentId}")
    public ApiResponse<DocumentResponse> get(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long documentId
    ) {
        return ApiResponse.success(documentService.getById(principal.userId(), documentId));
    }

    /**
     * 查询文档切片，用于前端切片预览。
     */
    @GetMapping("/documents/{documentId}/chunks")
    public ApiResponse<List<DocumentChunkResponse>> chunks(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long documentId
    ) {
        return ApiResponse.success(documentService.listChunks(principal.userId(), documentId));
    }

    /**
     * 删除文档，同时清理 MySQL 切片和 Chroma 向量。
     */
    @DeleteMapping("/documents/{documentId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long documentId
    ) {
        documentService.delete(principal.userId(), documentId);
        return ApiResponse.success();
    }

    /**
     * 查询知识库下的文档列表。
     */
    @GetMapping("/knowledge-bases/{knowledgeBaseId}/documents")
    public ApiResponse<List<DocumentResponse>> list(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long knowledgeBaseId
    ) {
        return ApiResponse.success(documentService.listByKnowledgeBase(principal.userId(), knowledgeBaseId));
    }
}
