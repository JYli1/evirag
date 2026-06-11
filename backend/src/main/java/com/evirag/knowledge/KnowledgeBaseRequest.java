package com.evirag.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建知识库请求。
 *
 * <p>userId 不允许由前端提交，控制器必须从 JWT 主体中读取当前用户身份。</p>
 */
public record KnowledgeBaseRequest(
        // 知识库名称会展示在左侧列表，不能为空且不能过长。
        @NotBlank(message = "知识库名称不能为空")
        @Size(max = 128, message = "知识库名称不能超过 128 个字符")
        String name,

        // 描述是可选字段，主要帮助用户区分不同资料集合。
        @Size(max = 2000, message = "知识库描述不能超过 2000 个字符")
        String description
) {
}
