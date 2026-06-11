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

    // 文档详情和删除都必须校验用户归属。
    Optional<Document> findByIdAndUserId(Long id, Long userId);

    // 知识库文档列表，按上传时间倒序展示。
    List<Document> findByKnowledgeBaseIdAndUserIdOrderByCreatedAtDesc(Long knowledgeBaseId, Long userId);

    // 管理端用户详情只展示最近 5 个文档。
    List<Document> findTop5ByUserIdOrderByCreatedAtDesc(Long userId);

    // 管理端统计该用户文档总数。
    long countByUserId(Long userId);

    // 管理端统计某用户成功/失败文档数量。
    long countByUserIdAndParseStatus(Long userId, DocumentStatus parseStatus);

    // 管理员首页统计全局成功/失败文档数量。
    long countByParseStatus(DocumentStatus parseStatus);

    // 管理员首页统计今日上传量。
    long countByCreatedAtBetween(Instant startInclusive, Instant endExclusive);
}
