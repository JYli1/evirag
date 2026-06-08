package com.evirag.chat;

import com.evirag.auth.JwtService.JwtPrincipal;
import com.evirag.chat.dto.ChatMessageResponse;
import com.evirag.chat.dto.ChatSessionResponse;
import com.evirag.chat.dto.CreateSessionRequest;
import com.evirag.chat.dto.SendMessageRequest;
import com.evirag.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 聊天 REST/SSE 接口。
 *
 * <p>REST 接口返回统一 ApiResponse；流式问答接口返回 SSE 事件流，事件名与前端约定保持稳定。</p>
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/kbs/{knowledgeBaseId}/sessions")
    public ApiResponse<List<ChatSessionResponse>> listSessions(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long knowledgeBaseId
    ) {
        return ApiResponse.success(chatService.listSessions(principal.userId(), knowledgeBaseId));
    }

    @GetMapping("/sessions")
    public ApiResponse<List<ChatSessionResponse>> listFreeSessions(
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        return ApiResponse.success(chatService.listSessions(principal.userId(), null));
    }

    @PostMapping("/kbs/{knowledgeBaseId}/sessions")
    public ApiResponse<ChatSessionResponse> createSession(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long knowledgeBaseId,
            @Valid @RequestBody(required = false) CreateSessionRequest request
    ) {
        return ApiResponse.success(chatService.createSession(principal.userId(), knowledgeBaseId, request));
    }

    @PostMapping("/sessions")
    public ApiResponse<ChatSessionResponse> createFreeSession(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody(required = false) CreateSessionRequest request
    ) {
        return ApiResponse.success(chatService.createSession(principal.userId(), null, request));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<List<ChatMessageResponse>> listMessages(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long sessionId
    ) {
        return ApiResponse.success(chatService.listMessages(principal.userId(), sessionId));
    }

    @PostMapping(value = "/sessions/{sessionId}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long sessionId,
            @Valid @RequestBody SendMessageRequest request
    ) {
        return chatService.streamMessage(principal.userId(), sessionId, request);
    }
}
