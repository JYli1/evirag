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

    Optional<ChatSession> findByIdAndUserId(Long id, Long userId);

    List<ChatSession> findByKnowledgeBaseIdAndUserIdOrderByUpdatedAtDesc(Long knowledgeBaseId, Long userId);

    List<ChatSession> findByKnowledgeBaseIdIsNullAndUserIdOrderByUpdatedAtDesc(Long userId);
}
