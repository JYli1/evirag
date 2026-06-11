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

    // 普通用户按 id 查询时必须带 userId，防止访问别人的知识库。
    Optional<KnowledgeBase> findByIdAndUserId(Long id, Long userId);

    // 左侧知识库列表按创建时间倒序展示。
    List<KnowledgeBase> findByUserIdOrderByCreatedAtDesc(Long userId);

    // 管理员查看用户详情时统计该用户知识库数量。
    long countByUserId(Long userId);
}
