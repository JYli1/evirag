package com.evirag.rag;

/**
 * Query rewrite 结果。
 *
 * <p>改写后的问题只用于检索，不替换用户在聊天窗口里看到的原始问题。</p>
 */
public record RewriteResult(String originalQuery, String rewrittenQuery, boolean rewritten) {
}
