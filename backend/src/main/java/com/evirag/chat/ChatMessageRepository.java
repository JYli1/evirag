package com.evirag.chat;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 聊天消息仓储。
 *
 * <p>消息查询同样绑定 userId；RAG 历史消息按时间倒序取最近若干条后再恢复正序。</p>
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findBySessionIdAndUserIdOrderByCreatedAtAsc(Long sessionId, Long userId);

    List<ChatMessage> findTop20BySessionIdAndUserIdOrderByCreatedAtDesc(Long sessionId, Long userId);

    List<ChatMessage> findTop5ByUserIdOrderByCreatedAtDesc(Long userId);

    long countByRole(String role);

    long countByUserIdAndRole(Long userId, String role);

    @Query("select sum(length(message.content)) from ChatMessage message")
    Long sumContentLength();

    @Query("select sum(length(message.content)) from ChatMessage message where message.userId = :userId")
    Long sumContentLengthByUserId(@Param("userId") Long userId);
}
