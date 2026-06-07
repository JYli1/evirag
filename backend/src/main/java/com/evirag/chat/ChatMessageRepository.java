package com.evirag.chat;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 聊天消息仓储。
 *
 * <p>消息查询同样绑定 userId；RAG 历史消息按时间倒序取最近若干条后再恢复正序。</p>
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findBySessionIdAndUserIdOrderByCreatedAtAsc(Long sessionId, Long userId);

    List<ChatMessage> findTop20BySessionIdAndUserIdOrderByCreatedAtDesc(Long sessionId, Long userId);
}
