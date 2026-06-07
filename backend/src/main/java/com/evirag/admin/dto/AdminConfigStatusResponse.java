package com.evirag.admin.dto;

import java.util.List;

/**
 * 管理员配置检查响应。
 *
 * <p>这里只返回配置项是否存在，不返回真实 URL、账号、密码或 API Key，避免前端和浏览器日志泄露敏感信息。</p>
 */
public record AdminConfigStatusResponse(
        long missingCount,
        List<ConfigStatusItem> items
) {

    /**
     * 单个配置项状态。
     */
    public record ConfigStatusItem(
            String key,
            String name,
            String group,
            boolean required,
            boolean secret,
            boolean configured,
            String message
    ) {
    }
}
