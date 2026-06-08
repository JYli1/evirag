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

    @PostMapping("/knowledge-bases/{knowledgeBaseId}/documents")
    public ApiResponse<DocumentResponse> upload(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long knowledgeBaseId,
            @RequestParam("file") MultipartFile file
    ) {
        return ApiResponse.success(documentService.upload(principal.userId(), knowledgeBaseId, file));
    }

    @GetMapping("/documents/{documentId}")
    public ApiResponse<DocumentResponse> get(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long documentId
    ) {
        return ApiResponse.success(documentService.getById(principal.userId(), documentId));
    }

    @GetMapping("/documents/{documentId}/chunks")
    public ApiResponse<List<DocumentChunkResponse>> chunks(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long documentId
    ) {
        return ApiResponse.success(documentService.listChunks(principal.userId(), documentId));
    }

    @DeleteMapping("/documents/{documentId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long documentId
    ) {
        documentService.delete(principal.userId(), documentId);
        return ApiResponse.success();
    }

    @GetMapping("/knowledge-bases/{knowledgeBaseId}/documents")
    public ApiResponse<List<DocumentResponse>> list(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long knowledgeBaseId
    ) {
        return ApiResponse.success(documentService.listByKnowledgeBase(principal.userId(), knowledgeBaseId));
    }
}
