package com.evirag.document;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文档切片数据访问层。
 *
 * <p>普通用户检索时不直接暴露该仓储；RAG 检索从 Chroma 召回后再按 chunk_id 回查 MySQL 元数据。</p>
 */
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(Long documentId);

    long countByKnowledgeBaseId(Long knowledgeBaseId);

    @Transactional
    @Modifying
    @Query("delete from DocumentChunk chunk where chunk.documentId = :documentId")
    void deleteByDocumentId(@Param("documentId") Long documentId);
}
