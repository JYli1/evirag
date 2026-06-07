package com.evirag.knowledge;

/**
 * 知识库不存在或当前用户无权访问。
 *
 * <p>对外统一成同一条消息，避免通过错误差异探测其他用户的知识库 ID。</p>
 */
public class KnowledgeBaseNotFoundException extends RuntimeException {

    public KnowledgeBaseNotFoundException() {
        super("知识库不存在或无权访问");
    }
}
