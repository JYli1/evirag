package com.evirag.knowledge;

import java.time.Instant;

/**
 * 知识库响应对象。
 *
 * <p>仅返回当前任务需要的元数据；管理端统计后续放在 admin 模块，不复用普通用户列表接口。</p>
 */
public record KnowledgeBaseResponse(
        Long id,
        String name,
        String description,
        String chromaCollection,
        String status,
        Instant createdAt,
        Instant updatedAt
) {

    public static KnowledgeBaseResponse from(KnowledgeBase knowledgeBase) {
        return new KnowledgeBaseResponse(
                knowledgeBase.getId(),
                knowledgeBase.getName(),
                knowledgeBase.getDescription(),
                knowledgeBase.getChromaCollection(),
                knowledgeBase.getStatus(),
                knowledgeBase.getCreatedAt(),
                knowledgeBase.getUpdatedAt()
        );
    }
}
