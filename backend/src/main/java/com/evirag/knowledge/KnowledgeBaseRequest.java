package com.evirag.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建知识库请求。
 *
 * <p>userId 不允许由前端提交，控制器必须从 JWT 主体中读取当前用户身份。</p>
 */
public record KnowledgeBaseRequest(
        @NotBlank(message = "知识库名称不能为空")
        @Size(max = 128, message = "知识库名称不能超过 128 个字符")
        String name,

        @Size(max = 2000, message = "知识库描述不能超过 2000 个字符")
        String description
) {
}
