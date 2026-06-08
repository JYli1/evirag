package com.evirag.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.evirag.embedding.EmbeddingClient;
import com.evirag.llm.LlmClient;
import com.evirag.llm.LlmMessage;
import com.evirag.retrieval.ChromaClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * RAG 编排服务测试。
 *
 * <p>RagService 不读取数据库，只使用调用方传入的历史消息和 collection；低相似度片段不能被丢弃，只做弱相关标记。</p>
 */
class RagServiceTest {

    private QueryRewriteService queryRewriteService;
    private EmbeddingClient embeddingClient;
    private ChromaClient chromaClient;
    private LlmClient llmClient;
    private RagService ragService;

    @BeforeEach
    void setUp() {
        queryRewriteService = org.mockito.Mockito.mock(QueryRewriteService.class);
        embeddingClient = org.mockito.Mockito.mock(EmbeddingClient.class);
        chromaClient = org.mockito.Mockito.mock(ChromaClient.class);
        llmClient = org.mockito.Mockito.mock(LlmClient.class);
        ragService = new RagService(queryRewriteService, embeddingClient, chromaClient, llmClient);
    }

    @Test
    void keepsLowScoreResultsAndUsesPassedHistoryInPrompt() {
        List<RagHistoryMessage> history = List.of(
                new RagHistoryMessage("user", "上一轮问题"),
                new RagHistoryMessage("assistant", "上一轮回答")
        );
        RagRequest request = new RagRequest(7L, 9L, "kb_collection", "它有什么限制？", history, 3, 0.35);
        when(queryRewriteService.rewrite("它有什么限制？", history))
                .thenReturn(new RewriteResult("它有什么限制？", "EviRAG 有什么限制？", true));
        when(embeddingClient.embedOne("EviRAG 有什么限制？")).thenReturn(List.of(0.1, 0.2, 0.3));
        when(chromaClient.query(eq("kb_collection"), eq(List.of(0.1, 0.2, 0.3)), eq(3), any()))
                .thenReturn(List.of(new ChromaClient.ChromaQueryResult(
                        "vector-1",
                        "低相似度但仍然要展示的证据片段",
                        0.12,
                        Map.of(
                                "document_id", 11L,
                                "chunk_id", 12L,
                                "chunk_index", 2,
                                "source_title", "课程设计指导书",
                                "source_location", "paragraph-3"
                        )
                )));
        when(llmClient.complete(any())).thenReturn("根据证据，限制包括依赖知识库质量。");

        RagResponse response = ragService.answer(request);

        assertThat(response.answer()).isEqualTo("根据证据，限制包括依赖知识库质量。");
        assertThat(response.rewrittenQuery()).isEqualTo("EviRAG 有什么限制？");
        assertThat(response.lowConfidence()).isTrue();
        assertThat(response.citations()).hasSize(1);
        assertThat(response.citations().get(0).lowScore()).isTrue();
        assertThat(response.citations().get(0).content()).contains("仍然要展示");

        ArgumentCaptor<Map<String, Object>> whereCaptor = ArgumentCaptor.forClass(Map.class);
        verify(chromaClient).query(eq("kb_collection"), eq(List.of(0.1, 0.2, 0.3)), eq(3), whereCaptor.capture());
        assertThat(whereCaptor.getValue())
                .containsEntry("$and", List.of(
                        Map.of("user_id", 7L),
                        Map.of("knowledge_base_id", 9L)
                ));

        ArgumentCaptor<List<LlmMessage>> promptCaptor = ArgumentCaptor.forClass(List.class);
        verify(llmClient).complete(promptCaptor.capture());
        assertThat(promptCaptor.getValue())
                .extracting(LlmMessage::content)
                .anyMatch(content -> content.contains("上一轮回答"))
                .anyMatch(content -> content.contains("低相似度但仍然要展示的证据片段"));
    }

    @Test
    void returnsKnowledgeBaseEmptyMessageWhenChromaHasNoResults() {
        RagRequest request = new RagRequest(7L, 9L, "kb_collection", "系统架构是什么？", List.of(), 5, 0.35);
        when(queryRewriteService.rewrite("系统架构是什么？", List.of()))
                .thenReturn(new RewriteResult("系统架构是什么？", "系统架构是什么？", false));
        when(embeddingClient.embedOne("系统架构是什么？")).thenReturn(List.of(0.4, 0.5));
        when(chromaClient.query(eq("kb_collection"), eq(List.of(0.4, 0.5)), eq(5), any()))
                .thenReturn(List.of());

        RagResponse response = ragService.answer(request);

        assertThat(response.answer()).contains("当前知识库");
        assertThat(response.citations()).isEmpty();
        assertThat(response.lowConfidence()).isTrue();
        verify(llmClient, never()).complete(any());
    }
}
