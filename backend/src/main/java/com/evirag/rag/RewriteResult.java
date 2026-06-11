package com.evirag.rag;

/**
 * Query rewrite 结果。
 *
 * <p>改写后的问题只用于检索，不替换用户在聊天窗口里看到的原始问题。</p>
 */
public record RewriteResult(
        // 用户原始问题。
        String originalQuery,
        // 用于向量检索的问题。
        String rewrittenQuery,
        // true 表示确实发生了改写。
        boolean rewritten
) {
}
