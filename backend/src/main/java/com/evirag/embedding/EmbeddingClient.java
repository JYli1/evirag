package com.evirag.embedding;

import java.util.List;

/**
 * Embedding 客户端接口。
 *
 * <p>该接口只负责把文本转换为向量，不做文档解析、切片、检索或聊天模型调用，保持模块边界清晰。</p>
 */
public interface EmbeddingClient {

    /**
     * 批量生成 embedding，返回顺序必须和输入文本顺序一致。
     */
    List<List<Double>> embed(List<String> inputs);

    /**
     * 单条文本 embedding 的便捷入口，后续 RAG 检索问题向量化会复用。
     */
    default List<Double> embedOne(String input) {
        // 默认方法可以让实现类只关心批量 embed；单条文本通过包装成 List 复用同一套逻辑。
        return embed(List.of(input)).get(0);
    }
}
