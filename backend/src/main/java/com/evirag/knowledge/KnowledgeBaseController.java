package com.evirag.knowledge;

import com.evirag.auth.JwtService.JwtPrincipal;
import com.evirag.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

/**
 * 知识库 REST 接口。
 *
 * <p>控制器只从 {@link JwtPrincipal#userId()} 获取用户身份，不接受前端传入 userId。</p>
 */
@RestController
@RequestMapping("/api/knowledge-bases")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    /**
     * 为当前登录用户创建知识库。
     */
    @PostMapping
    public ApiResponse<KnowledgeBaseResponse> create(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody KnowledgeBaseRequest request
    ) {
        return ApiResponse.success(knowledgeBaseService.create(principal.userId(), request));
    }

    /**
     * 查询当前登录用户的知识库列表。
     */
    @GetMapping
    public ApiResponse<List<KnowledgeBaseResponse>> list(@AuthenticationPrincipal JwtPrincipal principal) {
        return ApiResponse.success(knowledgeBaseService.listByCurrentUser(principal.userId()));
    }

    /**
     * 查询当前用户拥有的单个知识库。
     */
    @GetMapping("/{knowledgeBaseId}")
    public ApiResponse<KnowledgeBaseResponse> get(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long knowledgeBaseId
    ) {
        return ApiResponse.success(knowledgeBaseService.getById(principal.userId(), knowledgeBaseId));
    }
}
