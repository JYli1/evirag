package com.evirag.knowledge;

import java.time.Instant;

/**
 * 知识库响应对象。
 *
 * <p>仅返回当前任务需要的元数据；管理端统计后续放在 admin 模块，不复用普通用户列表接口。</p>
 */
public record KnowledgeBaseResponse(
        // 知识库主键，后续上传文档和创建会话都会用到。
        Long id,
        // 展示名称。
        String name,
        // 用户填写的说明。
        String description,
        // 对应的 Chroma collection 名称，便于调试索引问题。
        String chromaCollection,
        // 预留状态字段，目前用于表达知识库是否可用。
        String status,
        // 创建时间。
        Instant createdAt,
        // 更新时间。
        Instant updatedAt
) {

    /**
     * 从实体转换响应，避免 Controller 直接暴露 JPA Entity。
     */
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
