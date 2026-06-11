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

    // 前端加载历史消息时按时间正序显示。
    List<ChatMessage> findBySessionIdAndUserIdOrderByCreatedAtAsc(Long sessionId, Long userId);

    // RAG 只取最近若干条上下文，避免 prompt 过长。
    List<ChatMessage> findTop20BySessionIdAndUserIdOrderByCreatedAtDesc(Long sessionId, Long userId);

    // 管理员查看用户详情时展示最近消息。
    List<ChatMessage> findTop5ByUserIdOrderByCreatedAtDesc(Long userId);

    // 管理员首页统计全局提问次数。
    long countByRole(String role);

    // 管理员用户详情统计用户消息和助手消息。
    long countByUserIdAndRole(Long userId, String role);

    // 估算全局聊天 token，length 是数据库字符串长度。
    @Query("select sum(length(message.content)) from ChatMessage message")
    Long sumContentLength();

    // 估算单用户聊天 token。
    @Query("select sum(length(message.content)) from ChatMessage message where message.userId = :userId")
    Long sumContentLengthByUserId(@Param("userId") Long userId);
}
