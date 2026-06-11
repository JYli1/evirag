package com.evirag.chat;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 聊天会话仓储。
 *
 * <p>所有普通用户查询都带 userId，避免仅按 sessionId 查询导致越权。</p>
 */
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    // 查询单个会话时带 userId，避免用户猜 sessionId 读到别人的会话。
    Optional<ChatSession> findByIdAndUserId(Long id, Long userId);

    // 知识库会话列表，最近更新的排在前面。
    List<ChatSession> findByKnowledgeBaseIdAndUserIdOrderByUpdatedAtDesc(Long knowledgeBaseId, Long userId);

    // 自由会话列表，knowledgeBaseId 为空。
    List<ChatSession> findByKnowledgeBaseIdIsNullAndUserIdOrderByUpdatedAtDesc(Long userId);
}
