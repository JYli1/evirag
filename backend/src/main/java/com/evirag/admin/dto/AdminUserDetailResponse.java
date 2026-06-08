package com.evirag.admin.dto;

import com.evirag.chat.ChatMessage;
import com.evirag.document.Document;
import com.evirag.document.DocumentStatus;
import com.evirag.user.User;
import java.time.Instant;
import java.util.List;

/**
 * 管理员查看单个用户时的详情响应。
 *
 * <p>当前系统还没有模型服务返回的真实 token 账单，因此 token 字段使用文档切片估算值和消息长度估算值。</p>
 */
public record AdminUserDetailResponse(
        AdminUserResponse user,
        long knowledgeBaseCount,
        long documentCount,
        long readyDocumentCount,
        long failedDocumentCount,
        long chunkCount,
        long questionCount,
        long assistantMessageCount,
        long estimatedDocumentTokens,
        long estimatedChatTokens,
        long estimatedTotalTokens,
        List<RecentDocument> recentDocuments,
        List<RecentMessage> recentMessages
) {

    public static AdminUserDetailResponse of(
            User user,
            long knowledgeBaseCount,
            long documentCount,
            long readyDocumentCount,
            long failedDocumentCount,
            long chunkCount,
            long questionCount,
            long assistantMessageCount,
            long estimatedDocumentTokens,
            long estimatedChatTokens,
            List<Document> recentDocuments,
            List<ChatMessage> recentMessages
    ) {
        return new AdminUserDetailResponse(
                AdminUserResponse.from(user),
                knowledgeBaseCount,
                documentCount,
                readyDocumentCount,
                failedDocumentCount,
                chunkCount,
                questionCount,
                assistantMessageCount,
                estimatedDocumentTokens,
                estimatedChatTokens,
                estimatedDocumentTokens + estimatedChatTokens,
                recentDocuments.stream().map(RecentDocument::from).toList(),
                recentMessages.stream().map(RecentMessage::from).toList()
        );
    }

    public record RecentDocument(
            Long id,
            Long knowledgeBaseId,
            String originalFilename,
            DocumentStatus parseStatus,
            Integer chunkCount,
            Instant createdAt
    ) {

        public static RecentDocument from(Document document) {
            return new RecentDocument(
                    document.getId(),
                    document.getKnowledgeBaseId(),
                    document.getOriginalFilename(),
                    document.getParseStatus(),
                    document.getChunkCount(),
                    document.getCreatedAt()
            );
        }
    }

    public record RecentMessage(
            Long id,
            Long sessionId,
            String role,
            String preview,
            Instant createdAt
    ) {

        public static RecentMessage from(ChatMessage message) {
            return new RecentMessage(
                    message.getId(),
                    message.getSessionId(),
                    message.getRole(),
                    preview(message.getContent()),
                    message.getCreatedAt()
            );
        }

        private static String preview(String content) {
            if (content == null || content.isBlank()) {
                return "";
            }
            String normalized = content.replaceAll("\\s+", " ").trim();
            return normalized.length() <= 96 ? normalized : normalized.substring(0, 96) + "...";
        }
    }
}
