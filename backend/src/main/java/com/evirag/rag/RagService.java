package com.evirag.rag;

import com.evirag.embedding.EmbeddingClient;
import com.evirag.llm.LlmClient;
import com.evirag.llm.LlmMessage;
import com.evirag.retrieval.ChromaClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * RAG 编排服务。
 *
 * <p>该服务不直接读取数据库。它接收 chat 模块传入的用户、知识库、历史消息和 Chroma collection，
 * 完成 query rewrite、embedding、Chroma Top-K 检索、prompt 组装和 LLM 调用。</p>
 */
@Service
public class RagService {

    private final QueryRewriteService queryRewriteService;
    private final EmbeddingClient embeddingClient;
    private final ChromaClient chromaClient;
    private final LlmClient llmClient;

    public RagService(
            QueryRewriteService queryRewriteService,
            EmbeddingClient embeddingClient,
            ChromaClient chromaClient,
            LlmClient llmClient
    ) {
        this.queryRewriteService = queryRewriteService;
        this.embeddingClient = embeddingClient;
        this.chromaClient = chromaClient;
        this.llmClient = llmClient;
    }

    public RagResponse answer(RagRequest request) {
        // 非流式入口主要给测试或后台调用使用：完整答案生成完后一次性返回。
        RewriteResult rewrite = queryRewriteService.rewrite(request.question(), request.historyMessages());
        List<RagCitation> citations = retrieve(request, rewrite.rewrittenQuery());
        if (citations.isEmpty() && !request.webSearchHasResults()) {
            return emptyResponse(rewrite.rewrittenQuery());
        }
        String answer = llmClient.complete(promptMessages(request, citations));
        return new RagResponse(answer, rewrite.rewrittenQuery(), citations, lowConfidence(citations));
    }

    public void streamAnswer(RagRequest request, RagStreamListener listener) {
        // RAG 的顺序可以理解为：
        // 1. 根据历史消息改写用户问题；2. 把问题转成向量；3. 去 Chroma 找相似切片；
        // 4. 把切片拼进 prompt；5. 调用 LLM 流式生成答案。
        RewriteResult rewrite = queryRewriteService.rewrite(request.question(), request.historyMessages());
        // listener 不直接依赖前端类型，RagService 只负责通知阶段；ChatService 再决定如何转成 SSE 事件。
        listener.onRetrievalStart(rewrite.rewrittenQuery());
        List<RagCitation> citations = retrieve(request, rewrite.rewrittenQuery());
        listener.onRetrievalDone(citations);
        if (citations.isEmpty() && !request.webSearchHasResults()) {
            // 没有检索结果时不请求 LLM，直接返回低置信业务提示，避免模型在没有证据时编答案。
            RagResponse response = emptyResponse(rewrite.rewrittenQuery());
            listener.onAnswerDelta(response.answer());
            listener.onAnswerDone(response);
            return;
        }

        StringBuilder answer = new StringBuilder();
        List<LlmMessage> messages = promptMessages(request, citations);
        listener.onLlmRequest(messages);
        llmClient.stream(messages, delta -> {
            // delta 是模型流式返回的一小段文本。这里累积完整答案，同时把 delta 透传给前端。
            answer.append(delta);
            listener.onAnswerDelta(delta);
        });
        listener.onLlmResponse(answer.toString());
        listener.onAnswerDone(new RagResponse(answer.toString(), rewrite.rewrittenQuery(), citations, lowConfidence(citations)));
    }

    private List<RagCitation> retrieve(RagRequest request, String rewrittenQuery) {
        // 查询向量必须使用“改写后的问题”，这样“它是谁”等依赖上下文的问题也能检索到正确内容。
        List<Double> embedding = embeddingClient.embedOne(rewrittenQuery);
        Map<String, Object> where = new LinkedHashMap<>();
        // where 条件限制只能检索当前用户、当前知识库的切片，避免不同用户的数据混在一起。
        where.put("$and", List.of(
                Map.of("user_id", request.userId()),
                Map.of("knowledge_base_id", request.knowledgeBaseId())
        ));
        return chromaClient.query(request.chromaCollection(), embedding, request.topK(), where)
                .stream()
                .map(result -> toCitation(result, request.lowScoreThreshold()))
                .toList();
    }

    private RagCitation toCitation(ChromaClient.ChromaQueryResult result, double lowScoreThreshold) {
        Map<String, Object> metadata = result.metadata() == null ? Map.of() : result.metadata();
        // Citation 是给 LLM 和前端共同使用的“证据片段”：既有原文，也有文档/切片定位信息。
        return new RagCitation(
                result.id(),
                result.document(),
                result.score(),
                result.score() < lowScoreThreshold,
                longValue(metadata.get("document_id")),
                longValue(metadata.get("chunk_id")),
                intValue(metadata.get("chunk_index")),
                stringValue(metadata.get("source_title")),
                stringValue(metadata.get("source_location")),
                metadata
        );
    }

    private List<LlmMessage> promptMessages(RagRequest request, List<RagCitation> citations) {
        List<LlmMessage> messages = new ArrayList<>();
        // system 消息是“规则说明”，用来约束模型不要乱编；user 消息里放用户问题和检索到的引用片段。
        messages.add(LlmMessage.system("""
                你是 EviRAG 的知识库问答助手。请基于给定引用片段和可选的【联网搜索资料】谨慎回答。
                如果知识库引用片段无法支撑结论，但联网搜索资料可以支撑，请明确说明信息来自联网搜索。
                如果两类资料都无法支撑结论，请明确说明“当前知识库和联网搜索中没有找到强相关依据”。
                回答使用中文，避免编造来源。
                """));
        if (request.historyMessages() != null) {
            for (RagHistoryMessage history : request.historyMessages()) {
                // 历史消息保留 role，让模型知道哪些内容是用户说的，哪些是助手已经回答过的。
                messages.add(new LlmMessage(history.role(), history.content()));
            }
        }
        // 用户问题和引用片段放在同一条 user 消息里，能让模型在回答时明确看到“问题”和“证据”。
        messages.add(LlmMessage.user("用户问题：\n" + request.question()
                + "\n\n引用片段：\n" + citationPrompt(citations)
                + webSearchPrompt(request)));
        return messages;
    }

    private String webSearchPrompt(RagRequest request) {
        String context = request.webSearchContext();
        if (context == null || context.isBlank()) {
            return "";
        }
        return "\n\n" + context;
    }

    private String citationPrompt(List<RagCitation> citations) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < citations.size(); i++) {
            RagCitation citation = citations.get(i);
            // 编号 [1]、[2] 方便模型和前端都能把答案依据对应回具体片段。
            builder.append("[").append(i + 1).append("] 相似度=")
                    .append(String.format(java.util.Locale.ROOT, "%.4f", citation.score()));
            if (citation.sourceTitle() != null) {
                builder.append(" 标题=").append(citation.sourceTitle());
            }
            if (citation.sourceLocation() != null) {
                builder.append(" 位置=").append(citation.sourceLocation());
            }
            builder.append("\n").append(citation.content()).append("\n\n");
        }
        return builder.toString();
    }

    private RagResponse emptyResponse(String rewrittenQuery) {
        // true 表示低置信，前端可以用它显示“缺少依据”的状态。
        return new RagResponse("当前知识库没有可检索文档，或 Chroma 没有返回匹配片段。", rewrittenQuery, List.of(), true);
    }

    private boolean lowConfidence(List<RagCitation> citations) {
        // 只要所有片段都低于阈值，就认为这次回答缺少强依据。
        return !citations.isEmpty() && citations.stream().allMatch(RagCitation::lowScore);
    }

    private Long longValue(Object value) {
        // Chroma metadata 反序列化后可能是 Integer、Long、Double 或 String，这里统一转成 Long。
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.valueOf(value.toString());
    }

    private Integer intValue(Object value) {
        // 同上，chunk_index 在不同 JSON 解析路径下可能不是固定 Java 类型。
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? null : Integer.valueOf(value.toString());
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
