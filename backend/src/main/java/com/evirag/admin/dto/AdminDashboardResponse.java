package com.evirag.admin.dto;

/**
 * 管理员首页统计响应。
 *
 * <p>这些数字用于前端管理面板的概览卡片，所有统计都来自 MySQL 主数据，不直接扫描 Chroma。</p>
 */
public record AdminDashboardResponse(
        long totalUsers,
        long activeUsers,
        long disabledUsers,
        long totalKnowledgeBases,
        long totalDocuments,
        long readyDocuments,
        long failedDocuments,
        long questionCount,
        long todayUploadCount,
        long missingConfigCount
) {
}
