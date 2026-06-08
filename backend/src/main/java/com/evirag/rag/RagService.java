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
        RewriteResult rewrite = queryRewriteService.rewrite(request.question(), request.historyMessages());
        List<RagCitation> citations = retrieve(request, rewrite.rewrittenQuery());
        if (citations.isEmpty()) {
            return emptyResponse(rewrite.rewrittenQuery());
        }
        String answer = llmClient.complete(promptMessages(request, citations));
        return new RagResponse(answer, rewrite.rewrittenQuery(), citations, lowConfidence(citations));
    }

    public void streamAnswer(RagRequest request, RagStreamListener listener) {
        RewriteResult rewrite = queryRewriteService.rewrite(request.question(), request.historyMessages());
        listener.onRetrievalStart(rewrite.rewrittenQuery());
        List<RagCitation> citations = retrieve(request, rewrite.rewrittenQuery());
        listener.onRetrievalDone(citations);
        if (citations.isEmpty()) {
            RagResponse response = emptyResponse(rewrite.rewrittenQuery());
            listener.onAnswerDelta(response.answer());
            listener.onAnswerDone(response);
            return;
        }

        StringBuilder answer = new StringBuilder();
        llmClient.stream(promptMessages(request, citations), delta -> {
            answer.append(delta);
            listener.onAnswerDelta(delta);
        });
        listener.onAnswerDone(new RagResponse(answer.toString(), rewrite.rewrittenQuery(), citations, lowConfidence(citations)));
    }

    private List<RagCitation> retrieve(RagRequest request, String rewrittenQuery) {
        List<Double> embedding = embeddingClient.embedOne(rewrittenQuery);
        Map<String, Object> where = new LinkedHashMap<>();
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
        messages.add(LlmMessage.system("""
                你是 EviRAG 的知识库问答助手。只能基于给定引用片段谨慎回答。
                如果引用片段无法支撑结论，请明确说明“当前知识库中没有找到强相关依据”。
                回答使用中文，避免编造来源。
                """));
        if (request.historyMessages() != null) {
            for (RagHistoryMessage history : request.historyMessages()) {
                messages.add(new LlmMessage(history.role(), history.content()));
            }
        }
        messages.add(LlmMessage.user("用户问题：\n" + request.question() + "\n\n引用片段：\n" + citationPrompt(citations)));
        return messages;
    }

    private String citationPrompt(List<RagCitation> citations) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < citations.size(); i++) {
            RagCitation citation = citations.get(i);
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
        return new RagResponse("当前知识库没有可检索文档，或 Chroma 没有返回匹配片段。", rewrittenQuery, List.of(), true);
    }

    private boolean lowConfidence(List<RagCitation> citations) {
        return !citations.isEmpty() && citations.stream().allMatch(RagCitation::lowScore);
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.valueOf(value.toString());
    }

    private Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? null : Integer.valueOf(value.toString());
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
