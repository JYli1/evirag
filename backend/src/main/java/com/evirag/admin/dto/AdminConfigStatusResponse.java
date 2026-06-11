package com.evirag.admin.dto;

import java.util.List;

/**
 * 管理员配置检查响应。
 *
 * <p>这里只返回配置项是否存在，不返回真实 URL、账号、密码或 API Key，避免前端和浏览器日志泄露敏感信息。</p>
 */
public record AdminConfigStatusResponse(
        // 必填但未配置的数量，管理员首页可直接显示风险数字。
        long missingCount,
        // 每个配置项的检查结果。
        List<ConfigStatusItem> items
) {

    /**
     * 单个配置项状态。
     */
    public record ConfigStatusItem(
            // 环境变量或配置键名。
            String key,
            // 给管理员看的中文名称。
            String name,
            // 配置分组，例如数据库、大模型、邮箱。
            String group,
            // true 表示缺失会影响核心功能。
            boolean required,
            // true 表示真实值敏感，响应中只能展示是否已配置。
            boolean secret,
            // true 表示当前环境能读到有效值。
            boolean configured,
            // 给前端展示的简短说明。
            String message
    ) {
    }
}
