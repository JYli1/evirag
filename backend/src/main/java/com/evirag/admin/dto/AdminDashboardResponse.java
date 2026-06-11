package com.evirag.admin.dto;

/**
 * 管理员首页统计响应。
 *
 * <p>这些数字用于前端管理面板的概览卡片，所有统计都来自 MySQL 主数据，不直接扫描 Chroma。</p>
 */
public record AdminDashboardResponse(
        // 系统注册用户总数，用于管理员首页总览。
        long totalUsers,
        // 当前允许登录的用户数量。
        long activeUsers,
        // 已被管理员禁用的用户数量。
        long disabledUsers,
        // 所有用户创建的知识库总数。
        long totalKnowledgeBases,
        // 所有上传文档总数。
        long totalDocuments,
        // 已成功解析并完成索引的文档数量。
        long readyDocuments,
        // 解析、切片、embedding 或 Chroma 入库失败的文档数量。
        long failedDocuments,
        // 用户问题数量，按 USER 角色聊天消息统计。
        long questionCount,
        // 今天上传的文档数量，用于观察系统近期使用情况。
        long todayUploadCount,
        // 文档切片 token 与聊天内容长度的估算值，不代表服务商真实账单。
        long estimatedTotalTokens,
        // 必填配置缺失数量，管理员可据此检查 .env。
        long missingConfigCount
) {
}
