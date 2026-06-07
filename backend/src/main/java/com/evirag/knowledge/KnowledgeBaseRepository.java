package com.evirag.knowledge;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 知识库数据访问层。
 *
 * <p>普通用户路径只暴露带 userId 的查询方法，防止调用方误用全局 id 查询造成越权。</p>
 */
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, Long> {

    Optional<KnowledgeBase> findByIdAndUserId(Long id, Long userId);

    List<KnowledgeBase> findByUserIdOrderByCreatedAtDesc(Long userId);
}
