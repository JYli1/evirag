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
        // 用户基本信息，复用列表响应结构。
        AdminUserResponse user,
        // 该用户创建的知识库数量。
        long knowledgeBaseCount,
        // 该用户上传过的文档总数。
        long documentCount,
        // 该用户已索引成功的文档数。
        long readyDocumentCount,
        // 该用户处理失败的文档数。
        long failedDocumentCount,
        // 该用户全部文档切片数量。
        long chunkCount,
        // 该用户提出的问题数量。
        long questionCount,
        // 系统给该用户生成的助手消息数量。
        long assistantMessageCount,
        // 文档切片 token 估算。
        long estimatedDocumentTokens,
        // 聊天内容 token 估算。
        long estimatedChatTokens,
        // 文档和聊天估算 token 合计。
        long estimatedTotalTokens,
        // 最近上传的文档，便于管理员快速定位异常文件。
        List<RecentDocument> recentDocuments,
        // 最近聊天消息预览，便于管理员了解用户使用情况。
        List<RecentMessage> recentMessages
) {

    /**
     * 服务层传入实体和统计值，在这里组装成前端需要的一棵响应对象。
     */
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
            // 文档主键，前端可用于跳转或定位。
            Long id,
            // 文档所属知识库。
            Long knowledgeBaseId,
            // 用户上传时的原始文件名。
            String originalFilename,
            // 当前解析/索引状态。
            DocumentStatus parseStatus,
            // 已生成切片数。
            Integer chunkCount,
            // 上传时间。
            Instant createdAt
    ) {

        /**
         * 只保留近期文档列表需要展示的字段。
         */
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
            // 消息主键。
            Long id,
            // 所属会话。
            Long sessionId,
            // USER 或 ASSISTANT。
            String role,
            // 消息预览，避免管理员列表一次性渲染长文本。
            String preview,
            // 消息创建时间。
            Instant createdAt
    ) {

        /**
         * 生成近期消息预览时会裁剪长文本，避免管理面板被大段回答撑开。
         */
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
            // 把换行、多个空格折叠成一个空格，让表格里的一行预览更稳定。
            String normalized = content.replaceAll("\\s+", " ").trim();
            return normalized.length() <= 96 ? normalized : normalized.substring(0, 96) + "...";
        }
    }
}
