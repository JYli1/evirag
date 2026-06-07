package com.evirag.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.evirag.llm.LlmClient;
import com.evirag.llm.LlmException;
import com.evirag.llm.LlmMessage;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Query rewrite 服务测试。
 *
 * <p>改写能力只服务检索，不改变用户原始提问；LLM 改写失败时必须回退原问题，避免问答链路被改写阶段阻断。</p>
 */
class QueryRewriteServiceTest {

    private LlmClient llmClient;
    private QueryRewriteService queryRewriteService;

    @BeforeEach
    void setUp() {
        llmClient = org.mockito.Mockito.mock(LlmClient.class);
        queryRewriteService = new QueryRewriteService(llmClient);
    }

    @Test
    void rewritesContextDependentShortQuestionWithHistory() {
        List<RagHistoryMessage> history = List.of(
                new RagHistoryMessage("user", "EviRAG 的注册登录模块是什么？"),
                new RagHistoryMessage("assistant", "注册登录模块包含邮箱验证码、JWT 和权限控制。")
        );
        when(llmClient.complete(any())).thenReturn("EviRAG 注册登录模块有什么缺点？");

        RewriteResult result = queryRewriteService.rewrite("那它有什么缺点？", history);

        assertThat(result.originalQuery()).isEqualTo("那它有什么缺点？");
        assertThat(result.rewrittenQuery()).isEqualTo("EviRAG 注册登录模块有什么缺点？");
        assertThat(result.rewritten()).isTrue();
        ArgumentCaptor<List<LlmMessage>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(llmClient).complete(messagesCaptor.capture());
        assertThat(messagesCaptor.getValue().get(1).content())
                .contains("注册登录模块包含邮箱验证码")
                .contains("那它有什么缺点？");
    }

    @Test
    void fallsBackToOriginalQuestionWhenRewriteFails() {
        List<RagHistoryMessage> history = List.of(
                new RagHistoryMessage("assistant", "上一轮讨论的是文档解析。")
        );
        when(llmClient.complete(any())).thenThrow(new LlmException("HTTP 500: rewrite failed"));

        RewriteResult result = queryRewriteService.rewrite("它怎么做？", history);

        assertThat(result.originalQuery()).isEqualTo("它怎么做？");
        assertThat(result.rewrittenQuery()).isEqualTo("它怎么做？");
        assertThat(result.rewritten()).isFalse();
    }

    @Test
    void doesNotCallLlmWhenQuestionIsAlreadyStandalone() {
        RewriteResult result = queryRewriteService.rewrite(
                "请总结 EviRAG 文档解析模块的整体流程和失败处理策略",
                List.of(new RagHistoryMessage("assistant", "上一轮回答"))
        );

        assertThat(result.rewrittenQuery()).isEqualTo("请总结 EviRAG 文档解析模块的整体流程和失败处理策略");
        assertThat(result.rewritten()).isFalse();
        verify(llmClient, never()).complete(any());
    }
}
