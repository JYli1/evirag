package com.evirag.document;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 文档数据访问层。
 *
 * <p>普通用户详情和列表都必须带 userId，和 knowledge_bases/documents 表里的所有权约束保持一致。</p>
 */
public interface DocumentRepository extends JpaRepository<Document, Long> {

    Optional<Document> findByIdAndUserId(Long id, Long userId);

    List<Document> findByKnowledgeBaseIdAndUserIdOrderByCreatedAtDesc(Long knowledgeBaseId, Long userId);
}
