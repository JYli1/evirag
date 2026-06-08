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

    private static final long SSE_TIMEOUT_MILLIS = 180_000L;

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final RagService ragService;
    private final LlmClient llmClient;
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
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        this.chatStreamTaskExecutor = chatStreamTaskExecutor;
    }

    @Transactional
    public ChatSessionResponse createSession(Long userId, Long knowledgeBaseId, CreateSessionRequest request) {
        if (knowledgeBaseId != null) {
            knowledgeBaseRepository.findByIdAndUserId(knowledgeBaseId, userId)
                    .orElseThrow(KnowledgeBaseNotFoundException::new);
        }
        ChatSession session = ChatSession.create(userId, knowledgeBaseId, request == null ? null : request.title());
        return ChatSessionResponse.from(chatSessionRepository.save(session));
    }

    @Transactional(readOnly = true)
    public List<ChatSessionResponse> listSessions(Long userId, Long knowledgeBaseId) {
        if (knowledgeBaseId == null) {
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
        chatStreamTaskExecutor.execute(() -> runStream(userId, sessionId, request, emitter));
        return emitter;
    }

    private void runStream(Long userId, Long sessionId, SendMessageRequest request, SseEmitter emitter) {
        try {
            ChatSession session = chatSessionRepository.findByIdAndUserId(sessionId, userId)
                    .orElseThrow(ChatNotFoundException::new);
            List<RagHistoryMessage> historyMessages = historyMessages(userId, sessionId);
            chatMessageRepository.save(ChatMessage.user(sessionId, userId, request.content()));
            session.touch();
            chatSessionRepository.save(session);

            if (session.getKnowledgeBaseId() == null) {
                streamDirectLlmAnswer(emitter, sessionId, userId, request.content(), historyMessages);
                return;
            }

            KnowledgeBase knowledgeBase = knowledgeBaseRepository
                    .findByIdAndUserId(session.getKnowledgeBaseId(), userId)
                    .orElseThrow(KnowledgeBaseNotFoundException::new);

            if (documentChunkRepository.countByKnowledgeBaseId(knowledgeBase.getId()) == 0) {
                streamNoIndexedDocumentAnswer(emitter, sessionId, userId, request.content(), historyMessages);
                return;
            }

            RagRequest ragRequest = new RagRequest(
                    userId,
                    knowledgeBase.getId(),
                    knowledgeBase.getChromaCollection(),
                    request.content(),
                    historyMessages,
                    appProperties.getRag().getTopK(),
                    appProperties.getRag().getLowScoreThreshold()
            );
            ragService.streamAnswer(ragRequest, new SseRagStreamListener(emitter, sessionId, userId));
        } catch (Exception ex) {
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
            List<RagHistoryMessage> historyMessages
    ) throws Exception {
        String prefix = "当前知识库中还没有可检索内容，下面是不基于知识库的通用回答：";
        StringBuilder answer = new StringBuilder(prefix);
        List<RagCitation> emptyCitations = List.of();
        sendEvent(emitter, "retrieval_start", Map.of("query", query));
        sendEvent(emitter, "retrieval_done", Map.of("citations", emptyCitations));
        sendEvent(emitter, "answer_delta", Map.of("delta", prefix));
        llmClient.stream(directPromptMessages(query, historyMessages), delta -> {
            answer.append(delta);
            sendEvent(emitter, "answer_delta", Map.of("delta", delta));
        });
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
            List<RagHistoryMessage> historyMessages
    ) throws Exception {
        StringBuilder answer = new StringBuilder();
        List<RagCitation> emptyCitations = List.of();
        sendEvent(emitter, "retrieval_start", Map.of("query", query));
        sendEvent(emitter, "retrieval_done", Map.of("citations", emptyCitations));
        llmClient.stream(directPromptMessages(query, historyMessages), delta -> {
            answer.append(delta);
            sendEvent(emitter, "answer_delta", Map.of("delta", delta));
        });
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

    private List<LlmMessage> directPromptMessages(String query, List<RagHistoryMessage> historyMessages) {
        List<LlmMessage> messages = new ArrayList<>();
        messages.add(LlmMessage.system("你是 EviRAG 的通用中文助手。当前回答不使用知识库引用，请直接、准确、简洁地回答用户问题。"));
        for (RagHistoryMessage history : historyMessages) {
            messages.add(new LlmMessage(history.role(), history.content()));
        }
        messages.add(LlmMessage.user(query));
        return messages;
    }

    /**
     * 历史消息按数据库倒序读取后恢复正序，只取最近 N 轮，避免 prompt 过长。
     */
    private List<RagHistoryMessage> historyMessages(Long userId, Long sessionId) {
        int limit = Math.max(0, appProperties.getRag().getHistoryTurns() * 2);
        if (limit == 0) {
            return List.of();
        }
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
        if (ChatMessage.ROLE_ASSISTANT.equals(role)) {
            return "assistant";
        }
        return "user";
    }

    private class SseRagStreamListener implements RagStreamListener {

        private final SseEmitter emitter;
        private final Long sessionId;
        private final Long userId;
        private final StringBuilder answer = new StringBuilder();

        private SseRagStreamListener(SseEmitter emitter, Long sessionId, Long userId) {
            this.emitter = emitter;
            this.sessionId = sessionId;
            this.userId = userId;
        }

        @Override
        public void onRetrievalStart(String query) {
            sendEvent(emitter, "retrieval_start", Map.of("query", query));
        }

        @Override
        public void onRetrievalDone(List<RagCitation> citations) {
            sendEvent(emitter, "retrieval_done", Map.of("citations", citations));
        }

        @Override
        public void onAnswerDelta(String delta) {
            answer.append(delta);
            sendEvent(emitter, "answer_delta", Map.of("delta", delta));
        }

        @Override
        public void onAnswerDone(RagResponse response) {
            try {
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
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException ex) {
            throw new IllegalStateException("SSE 事件发送失败", ex);
        }
    }

    private Map<String, Object> errorPayload(String stage, String message, String rawSummary) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stage", stage);
        payload.put("message", message);
        payload.put("rawSummary", rawSummary);
        return payload;
    }

    private String stageOf(Exception ex) {
        if (ex instanceof LlmException llmException) {
            return llmException.getStage();
        }
        if (ex instanceof ChromaException chromaException) {
            return chromaException.getStage();
        }
        return "CHAT";
    }

    private String rawSummary(Exception ex) {
        if (ex instanceof LlmException llmException) {
            return llmException.getRawSummary();
        }
        if (ex instanceof ChromaException chromaException) {
            return chromaException.getRawSummary();
        }
        String raw = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        return raw.replaceAll("(?i)(api[_-]?key|secret|token|password|authorization)=\\S+", "$1=***");
    }
}
