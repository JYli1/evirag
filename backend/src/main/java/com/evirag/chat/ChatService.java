package com.evirag.chat;

import com.evirag.chat.dto.ChatMessageResponse;
import com.evirag.chat.dto.ChatSessionResponse;
import com.evirag.chat.dto.CreateSessionRequest;
import com.evirag.chat.dto.SendMessageRequest;
import com.evirag.config.AppProperties;
import com.evirag.document.DocumentChunkRepository;
import com.evirag.knowledge.KnowledgeBase;
import com.evirag.knowledge.KnowledgeBaseNotFoundException;
import com.evirag.knowledge.KnowledgeBaseRepository;
import com.evirag.llm.LlmException;
import com.evirag.llm.LlmClient;
import com.evirag.llm.LlmMessage;
import com.evirag.rag.RagCitation;
import com.evirag.rag.RagHistoryMessage;
import com.evirag.rag.RagRequest;
import com.evirag.rag.RagResponse;
import com.evirag.rag.RagService;
import com.evirag.rag.RagStreamListener;
import com.evirag.retrieval.ChromaException;
import com.evirag.websearch.TavilyException;
import com.evirag.websearch.TavilyWebSearchService;
import com.evirag.websearch.WebSearchContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 聊天业务服务。
 *
 * <p>该模块是 RAG 的数据库边界：它负责读取会话、裁剪历史消息、保存用户问题和助手回答；
 * 真正的检索、prompt 组装和 LLM 调用交给 {@link RagService}。</p>
 */
@Service
public class ChatService {

    /**
     * SSE 最多保持 3 分钟。超过这个时间通常说明外部模型或网络卡住，前端应结束等待。
     */
    private static final long SSE_TIMEOUT_MILLIS = 180_000L;

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final RagService ragService;
    private final LlmClient llmClient;
    private final TavilyWebSearchService tavilyWebSearchService;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final Executor chatStreamTaskExecutor;

    public ChatService(
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            KnowledgeBaseRepository knowledgeBaseRepository,
            DocumentChunkRepository documentChunkRepository,
            RagService ragService,
            LlmClient llmClient,
            TavilyWebSearchService tavilyWebSearchService,
            AppProperties appProperties,
            ObjectMapper objectMapper,
            @Qualifier("chatStreamTaskExecutor") Executor chatStreamTaskExecutor
    ) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.ragService = ragService;
        this.llmClient = llmClient;
        this.tavilyWebSearchService = tavilyWebSearchService;
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        this.chatStreamTaskExecutor = chatStreamTaskExecutor;
    }

    @Transactional
    public ChatSessionResponse createSession(Long userId, Long knowledgeBaseId, CreateSessionRequest request) {
        if (knowledgeBaseId != null) {
            // 创建知识库会话前先校验归属，防止用户用别人的 knowledgeBaseId 建会话。
            knowledgeBaseRepository.findByIdAndUserId(knowledgeBaseId, userId)
                    .orElseThrow(KnowledgeBaseNotFoundException::new);
        }
        // knowledgeBaseId 为空表示普通聊天；不为空表示绑定某个知识库做 RAG。
        ChatSession session = ChatSession.create(userId, knowledgeBaseId, request == null ? null : request.title());
        return ChatSessionResponse.from(chatSessionRepository.save(session));
    }

    @Transactional(readOnly = true)
    public List<ChatSessionResponse> listSessions(Long userId, Long knowledgeBaseId) {
        if (knowledgeBaseId == null) {
            // 普通聊天会话单独查询，避免它和知识库会话混在一个列表里。
            return chatSessionRepository.findByKnowledgeBaseIdIsNullAndUserIdOrderByUpdatedAtDesc(userId)
                    .stream()
                    .map(ChatSessionResponse::from)
                    .toList();
        }
        knowledgeBaseRepository.findByIdAndUserId(knowledgeBaseId, userId)
                .orElseThrow(KnowledgeBaseNotFoundException::new);
        return chatSessionRepository.findByKnowledgeBaseIdAndUserIdOrderByUpdatedAtDesc(knowledgeBaseId, userId)
                .stream()
                .map(ChatSessionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> listMessages(Long userId, Long sessionId) {
        // 先确认会话归属，再查消息。否则只凭 sessionId 可能读到其他用户的聊天记录。
        chatSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(ChatNotFoundException::new);
        return chatMessageRepository.findBySessionIdAndUserIdOrderByCreatedAtAsc(sessionId, userId)
                .stream()
                .map(ChatMessageResponse::from)
                .toList();
    }

    /**
     * 创建 SSE emitter，并把耗时的 RAG 流式问答放入后台线程执行。
     */
    public SseEmitter streamMessage(Long userId, Long sessionId, SendMessageRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        // Controller 会立刻拿到 emitter 返回给浏览器；真正耗时的模型请求在后台线程里执行。
        chatStreamTaskExecutor.execute(() -> runStream(userId, sessionId, request, emitter));
        return emitter;
    }

    private void runStream(Long userId, Long sessionId, SendMessageRequest request, SseEmitter emitter) {
        try {
            // 这里是一次聊天请求的主流程。先确认会话确实属于当前用户，再保存用户问题，
            // 然后根据会话是否绑定知识库，决定走“自由 LLM 问答”还是“RAG 知识库问答”。
            ChatSession session = chatSessionRepository.findByIdAndUserId(sessionId, userId)
                    .orElseThrow(ChatNotFoundException::new);
            // 读取历史要在保存当前问题之前完成，避免当前问题重复出现在 prompt 历史里。
            List<RagHistoryMessage> historyMessages = historyMessages(userId, sessionId);
            chatMessageRepository.save(ChatMessage.user(sessionId, userId, request.content()));
            session.touch();
            chatSessionRepository.save(session);
            WebSearchContext webSearchContext = buildWebSearchContext(request, emitter);

            if (session.getKnowledgeBaseId() == null) {
                // 没有知识库的会话不做 embedding 和 Chroma 检索，直接把历史消息和当前问题发给 LLM。
                streamDirectLlmAnswer(emitter, sessionId, userId, request.content(), historyMessages, webSearchContext);
                return;
            }

            KnowledgeBase knowledgeBase = knowledgeBaseRepository
                    .findByIdAndUserId(session.getKnowledgeBaseId(), userId)
                    .orElseThrow(KnowledgeBaseNotFoundException::new);

            if (documentChunkRepository.countByKnowledgeBaseId(knowledgeBase.getId()) == 0) {
                // 选了知识库但还没有成功索引的切片时，也不要强行查 Chroma。
                // 这样用户能得到一个清楚的业务提示，而不是看到 collection 不存在之类的底层错误。
                streamNoIndexedDocumentAnswer(emitter, sessionId, userId, request.content(), historyMessages, webSearchContext);
                return;
            }

            RagRequest ragRequest = new RagRequest(
                    userId,
                    knowledgeBase.getId(),
                    knowledgeBase.getChromaCollection(),
                    request.content(),
                    historyMessages,
                    appProperties.getRag().getTopK(),
                    appProperties.getRag().getLowScoreThreshold(),
                    webSearchContext.promptText(),
                    webSearchContext.hasResults()
            );
            // 从这里开始交给 rag 模块：改写问题、做 embedding、查 Chroma、组 prompt、流式调用 LLM。
            ragService.streamAnswer(ragRequest, new SseRagStreamListener(emitter, sessionId, userId));
        } catch (Exception ex) {
            // SSE 请求中异常不能靠普通 ControllerAdvice 返回 JSON，因此这里手动发送 error 事件。
            sendEvent(emitter, "error", errorPayload(stageOf(ex), "问答生成失败", rawSummary(ex)));
            emitter.complete();
        }
    }

    /**
     * 知识库还没有成功索引的切片时直接返回业务提示。
     *
     * <p>这样可以避免空知识库仍然调用 embedding 和 Chroma，最终把 Chroma 404 暴露给前端。</p>
     */
    private void streamNoIndexedDocumentAnswer(
            SseEmitter emitter,
            Long sessionId,
            Long userId,
            String query,
            List<RagHistoryMessage> historyMessages,
            WebSearchContext webSearchContext
    ) throws Exception {
        String prefix = webSearchContext.hasResults()
                ? "当前知识库中还没有可检索内容，下面结合联网搜索资料回答："
                : "当前知识库中还没有可检索内容，下面是不基于知识库的通用回答：";
        StringBuilder answer = new StringBuilder(prefix);
        List<RagCitation> emptyCitations = List.of();
        List<LlmMessage> messages = directPromptMessages(query, historyMessages, webSearchContext.promptText());
        // 前端过程日志依赖这些 SSE 事件。事件名不要随意改，否则前端解析不到对应阶段。
        // 即使没有真正检索，也发送 retrieval_start/done，是为了前端日志流程保持一致。
        sendEvent(emitter, "retrieval_start", Map.of("query", query));
        sendEvent(emitter, "retrieval_done", Map.of("citations", emptyCitations));
        sendDebugLog(emitter, "BACKEND->LLM", "POST /chat/completions", llmRequestSummary(messages));
        sendEvent(emitter, "answer_delta", Map.of("delta", prefix));
        llmClient.stream(messages, delta -> {
            answer.append(delta);
            sendEvent(emitter, "answer_delta", Map.of("delta", delta));
        });
        sendDebugLog(emitter, "LLM->BACKEND", "stream completed", answer.toString());
        // 低置信设为 true，因为这个回答没有知识库证据支撑。
        chatMessageRepository.save(ChatMessage.assistant(
                sessionId,
                userId,
                answer.toString(),
                objectMapper.writeValueAsString(emptyCitations),
                true
        ));
        sendEvent(emitter, "answer_done", Map.of(
                "answer", answer.toString(),
                "rewrittenQuery", query,
                "citations", emptyCitations,
                "lowConfidence", true
        ));
        emitter.complete();
    }

    private void streamDirectLlmAnswer(
            SseEmitter emitter,
            Long sessionId,
            Long userId,
            String query,
            List<RagHistoryMessage> historyMessages,
            WebSearchContext webSearchContext
    ) throws Exception {
        StringBuilder answer = new StringBuilder();
        List<RagCitation> emptyCitations = List.of();
        // 普通聊天没有知识库，但复用相同事件名，前端就不需要为普通聊天和 RAG 聊天写两套渲染逻辑。
        sendEvent(emitter, "retrieval_start", Map.of("query", query));
        sendEvent(emitter, "retrieval_done", Map.of("citations", emptyCitations));
        List<LlmMessage> messages = directPromptMessages(query, historyMessages, webSearchContext.promptText());
        sendDebugLog(emitter, "BACKEND->LLM", "POST /chat/completions", llmRequestSummary(messages));
        llmClient.stream(messages, delta -> {
            answer.append(delta);
            sendEvent(emitter, "answer_delta", Map.of("delta", delta));
        });
        sendDebugLog(emitter, "LLM->BACKEND", "stream completed", answer.toString());
        chatMessageRepository.save(ChatMessage.assistant(
                sessionId,
                userId,
                answer.toString(),
                objectMapper.writeValueAsString(emptyCitations),
                false
        ));
        sendEvent(emitter, "answer_done", Map.of(
                "answer", answer.toString(),
                "rewrittenQuery", query,
                "citations", emptyCitations,
                "lowConfidence", false
        ));
        emitter.complete();
    }

    private WebSearchContext buildWebSearchContext(SendMessageRequest request, SseEmitter emitter) {
        if (!Boolean.TRUE.equals(request.webSearchEnabled())) {
            return WebSearchContext.empty();
        }
        sendDebugLog(emitter, "BACKEND->TAVILY", "web search enabled", "用户已开启联网搜索。后端会先识别 URL；如存在 URL，先 Extract，再 Search。");
        WebSearchContext context = tavilyWebSearchService.buildContext(
                request.content(),
                (direction, title, detail) -> sendDebugLog(emitter, direction, title, detail)
        );
        sendDebugLog(
                emitter,
                "TAVILY->BACKEND",
                "web context ready",
                "extract=" + context.extractResultCount()
                        + ", search=" + context.searchResultCount()
                        + ", hasResults=" + context.hasResults()
        );
        return context;
    }

    private List<LlmMessage> directPromptMessages(String query, List<RagHistoryMessage> historyMessages, String webSearchContext) {
        List<LlmMessage> messages = new ArrayList<>();
        // 普通聊天的 system prompt 不要求引用知识库，只要求简洁准确。
        messages.add(LlmMessage.system("""
                你是 EviRAG 的通用中文助手。
                如果提供了【联网搜索资料】，可以结合资料回答，并优先说明来自网页的信息。
                如果没有联网资料，请直接、准确、简洁地回答用户问题。
                回答使用中文，避免编造来源。
                """));
        for (RagHistoryMessage history : historyMessages) {
            messages.add(new LlmMessage(history.role(), history.content()));
        }
        messages.add(LlmMessage.user(userPrompt(query, webSearchContext)));
        return messages;
    }

    private String userPrompt(String query, String webSearchContext) {
        if (webSearchContext == null || webSearchContext.isBlank()) {
            return query;
        }
        return "用户问题：\n" + query + "\n\n" + webSearchContext;
    }

    /**
     * 历史消息按数据库倒序读取后恢复正序，只取最近 N 轮，避免 prompt 过长。
     */
    private List<RagHistoryMessage> historyMessages(Long userId, Long sessionId) {
        int limit = Math.max(0, appProperties.getRag().getHistoryTurns() * 2);
        if (limit == 0) {
            return List.of();
        }
        // Repository 方法名固定取 Top20，这里再按配置 limit 截断，配置不能超过 10 轮历史。
        List<ChatMessage> recent = new ArrayList<>(chatMessageRepository
                .findTop20BySessionIdAndUserIdOrderByCreatedAtDesc(sessionId, userId)
                .stream()
                .limit(limit)
                .toList());
        recent.sort(Comparator.comparing(ChatMessage::getCreatedAt));
        return recent.stream()
                .map(message -> new RagHistoryMessage(toLlmRole(message.getRole()), message.getContent()))
                .toList();
    }

    private String toLlmRole(String role) {
        // 数据库里只保存 user/assistant 两种聊天角色；其他值统一按 user 处理更安全。
        if (ChatMessage.ROLE_ASSISTANT.equals(role)) {
            return "assistant";
        }
        return "user";
    }

    private class SseRagStreamListener implements RagStreamListener {

        private final SseEmitter emitter;
        private final Long sessionId;
        private final Long userId;
        /**
         * 用来累积 LLM delta。即使前端已经收到了流式片段，后端仍要保存完整回答到数据库。
         */
        private final StringBuilder answer = new StringBuilder();

        private SseRagStreamListener(SseEmitter emitter, Long sessionId, Long userId) {
            this.emitter = emitter;
            this.sessionId = sessionId;
            this.userId = userId;
        }

        @Override
        public void onRetrievalStart(String query) {
            // 通知前端“开始检索”，日志面板会展示改写后的查询语句。
            sendEvent(emitter, "retrieval_start", Map.of("query", query));
        }

        @Override
        public void onRetrievalDone(List<RagCitation> citations) {
            // citations 会同时用于证据面板和日志面板，前端无需再额外请求一次引用信息。
            sendEvent(emitter, "retrieval_done", Map.of("citations", citations));
        }

        @Override
        public void onLlmRequest(List<LlmMessage> messages) {
            sendDebugLog(emitter, "BACKEND->LLM", "POST /chat/completions", llmRequestSummary(messages));
        }

        @Override
        public void onAnswerDelta(String delta) {
            answer.append(delta);
            // answer_delta 是打字机效果的关键事件，前端只对最新回答逐段追加。
            sendEvent(emitter, "answer_delta", Map.of("delta", delta));
        }

        @Override
        public void onLlmResponse(String answer) {
            sendDebugLog(emitter, "LLM->BACKEND", "stream completed", answer);
        }

        @Override
        public void onAnswerDone(RagResponse response) {
            try {
                // LLM 流式返回时，answer_delta 会一段段追加到 answer。
                // 某些实现会在 answer_done 里再次给出完整答案，所以这里优先用 response.answer()。
                String finalAnswer = response.answer() == null || response.answer().isBlank()
                        ? answer.toString()
                        : response.answer();
                chatMessageRepository.save(ChatMessage.assistant(
                        sessionId,
                        userId,
                        finalAnswer,
                        objectMapper.writeValueAsString(response.citations()),
                        response.lowConfidence()
                ));
                sendEvent(emitter, "answer_done", Map.of(
                        "answer", finalAnswer,
                        "rewrittenQuery", response.rewrittenQuery(),
                        "citations", response.citations(),
                        "lowConfidence", response.lowConfidence()
                ));
                emitter.complete();
            } catch (Exception ex) {
                sendEvent(emitter, "error", errorPayload(stageOf(ex), "保存回答失败", rawSummary(ex)));
                emitter.complete();
            }
        }
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            // Spring 会把这里的 data 序列化成 JSON 并以 Server-Sent Events 格式写给浏览器。
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException ex) {
            throw new IllegalStateException("SSE 事件发送失败", ex);
        }
    }

    private void sendDebugLog(SseEmitter emitter, String direction, String title, String detail) {
        // debug_log 只给用户排查请求链路使用，不参与业务数据保存。
        sendEvent(emitter, "debug_log", Map.of(
                "direction", direction,
                "title", title,
                "detail", detail == null ? "" : detail
        ));
    }

    private String llmRequestSummary(List<LlmMessage> messages) {
        Map<String, Object> summary = new LinkedHashMap<>();
        // 这里记录完整请求轮廓和截断后的 messages，方便定位模型、baseUrl、prompt 是否配置正确。
        summary.put("baseUrl", appProperties.getLlm().getBaseUrl());
        summary.put("endpoint", "/chat/completions");
        summary.put("model", appProperties.getLlm().getModel());
        summary.put("stream", true);
        summary.put("messageCount", messages.size());
        summary.put("messages", messages.stream()
                .map(message -> Map.of(
                        "role", message.role(),
                        "content", message.content() == null ? "" : message.content()
                ))
                .toList());
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (Exception ex) {
            return summary.toString();
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        // 日志展示需要可读，但不能让超长 prompt 把 SSE 消息和前端面板撑爆。
        return value.substring(0, maxLength) + "...[truncated]";
    }

    private Map<String, Object> errorPayload(String stage, String message, String rawSummary) {
        Map<String, Object> payload = new LinkedHashMap<>();
        // stage 用于前端给错误分类，例如 CHAT、LLM、CHROMA、EMBEDDING。
        payload.put("stage", stage);
        payload.put("message", message);
        payload.put("rawSummary", rawSummary);
        return payload;
    }

    private String stageOf(Exception ex) {
        // LlmException/ChromaException 自带阶段信息，普通异常统一归到 CHAT。
        if (ex instanceof LlmException llmException) {
            return llmException.getStage();
        }
        if (ex instanceof ChromaException chromaException) {
            return chromaException.getStage();
        }
        if (ex instanceof TavilyException tavilyException) {
            return tavilyException.getStage();
        }
        return "CHAT";
    }

    private String rawSummary(Exception ex) {
        // 对用户开放的日志要保留错误原文摘要，但必须做敏感信息脱敏。
        if (ex instanceof LlmException llmException) {
            return llmException.getRawSummary();
        }
        if (ex instanceof ChromaException chromaException) {
            return chromaException.getRawSummary();
        }
        if (ex instanceof TavilyException tavilyException) {
            return tavilyException.getRawSummary();
        }
        String raw = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        return raw.replaceAll("(?i)(api[_-]?key|secret|token|password|authorization)=\\S+", "$1=***");
    }
}
