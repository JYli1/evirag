package com.evirag.document;

import java.time.Instant;
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

    List<Document> findTop5ByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserId(Long userId);

    long countByUserIdAndParseStatus(Long userId, DocumentStatus parseStatus);

    long countByParseStatus(DocumentStatus parseStatus);

    long countByCreatedAtBetween(Instant startInclusive, Instant endExclusive);
}
