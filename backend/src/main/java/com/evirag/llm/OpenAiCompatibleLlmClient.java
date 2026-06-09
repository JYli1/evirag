package com.evirag.llm;

import com.evirag.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * OpenAI 兼容聊天模型客户端。
 *
 * <p>所有 LLM 配置都读取 {@code evirag.llm.*}，即最终来自 backend/.env。该客户端同时支持非流式和 SSE 风格流式响应。</p>
 */
@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleLlmClient(AppProperties appProperties, ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(appProperties.getLlm().getTimeoutSeconds()))
                .build();
    }

    @Override
    public String complete(List<LlmMessage> messages) {
        validateRequest(messages);
        boolean stream = false;
        try {
            HttpRequest request = request(messages, stream);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new LlmException(httpErrorSummary(response.statusCode(), response.body(), stream, messages));
            }
            return parseCompletion(response.body());
        } catch (LlmException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new LlmException(connectionErrorSummary(ex, stream, messages), ex);
        }
    }

    @Override
    public void stream(List<LlmMessage> messages, Consumer<String> onDelta) {
        validateRequest(messages);
        boolean stream = true;
        try {
            HttpRequest request = request(messages, stream);
            HttpResponse<Stream<String>> response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
            try (Stream<String> lines = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    String body = lines.collect(Collectors.joining("\n"));
                    throw new LlmException(httpErrorSummary(response.statusCode(), body, stream, messages));
                }
                lines.forEach(line -> handleStreamLine(line, onDelta));
            }
        } catch (LlmException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new LlmException(connectionErrorSummary(ex, stream, messages), ex);
        }
    }

    private HttpRequest request(List<LlmMessage> messages, boolean stream) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", appProperties.getLlm().getModel());
        body.put("messages", messages);
        body.put("stream", stream);
        // OpenAI-compatible 的聊天接口一般都是 POST /chat/completions。
        // 不同服务商只需要改 baseUrl、apiKey、model，代码层面的请求结构基本相同。
        return HttpRequest.newBuilder()
                .uri(chatCompletionsUri())
                .timeout(Duration.ofSeconds(appProperties.getLlm().getTimeoutSeconds()))
                .header("Authorization", "Bearer " + appProperties.getLlm().getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
    }

    private URI chatCompletionsUri() {
        String baseUrl = appProperties.getLlm().getBaseUrl().replaceAll("/+$", "");
        return URI.create(baseUrl + "/chat/completions");
    }

    private void validateRequest(List<LlmMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new LlmException("LLM 消息不能为空");
        }
        if (appProperties.getLlm().getBaseUrl() == null || appProperties.getLlm().getBaseUrl().isBlank()) {
            throw new LlmException("LLM_BASE_URL 不能为空");
        }
        if (appProperties.getLlm().getApiKey() == null || appProperties.getLlm().getApiKey().isBlank()) {
            throw new LlmException("LLM_API_KEY 不能为空");
        }
        if (appProperties.getLlm().getModel() == null || appProperties.getLlm().getModel().isBlank()) {
            throw new LlmException("LLM_MODEL 不能为空");
        }
    }

    private String parseCompletion(String body) throws Exception {
        JsonNode content = objectMapper.readTree(body)
                .path("choices")
                .path(0)
                .path("message")
                .path("content");
        if (content.isMissingNode()) {
            throw new LlmException("LLM 响应缺少 choices[0].message.content：" + sanitize(body));
        }
        return content.asText();
    }

    /**
     * 兼容 OpenAI SSE 格式：每行以 data: 开头，结束行为 data: [DONE]。
     */
    private void handleStreamLine(String line, Consumer<String> onDelta) {
        if (line == null || line.isBlank() || !line.startsWith("data:")) {
            return;
        }
        String payload = line.substring("data:".length()).trim();
        if ("[DONE]".equals(payload)) {
            return;
        }
        try {
            // 流式响应不是一次返回完整答案，而是一行一行返回 JSON。
            // 只有 delta.content 是真正文本时才追加；有些服务商会发 content:null 的控制片段，要忽略。
            JsonNode delta = objectMapper.readTree(payload)
                    .path("choices")
                    .path(0)
                    .path("delta")
                    .path("content");
            if (delta.isTextual() && !delta.asText().isEmpty()) {
                onDelta.accept(delta.asText());
            }
        } catch (Exception ex) {
            throw new LlmException("LLM 流式响应解析失败：" + sanitize(ex), ex);
        }
    }

    private String sanitize(Exception exception) {
        return sanitize(exceptionSummary(exception));
    }

    private String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("(?i)(api[_-]?key|secret|token|password|authorization)=\\S+", "$1=***");
    }

    private String httpErrorSummary(int statusCode, String body, boolean stream, List<LlmMessage> messages) {
        Map<String, Object> summary = baseDebugSummary(stream, messages);
        summary.put("errorType", "HTTP_ERROR");
        summary.put("statusCode", statusCode);
        summary.put("responseBody", sanitize(body));
        return "HTTP " + statusCode + ": " + toJson(summary);
    }

    private String connectionErrorSummary(Exception exception, boolean stream, List<LlmMessage> messages) {
        Map<String, Object> summary = baseDebugSummary(stream, messages);
        summary.put("errorType", "CLIENT_EXCEPTION");
        // 这里的错误不是“模型回答错了”，而是 HTTP 请求阶段就失败了，例如 baseUrl 不通或代理问题。
        summary.put("exception", exceptionSummary(exception));
        summary.put("hint", "请求没有拿到 LLM 的 HTTP 响应，通常是 baseUrl 不可达、网络/代理/DNS/防火墙问题，或服务地址与模型供应商不匹配。");
        return toJson(summary);
    }

    private Map<String, Object> baseDebugSummary(boolean stream, List<LlmMessage> messages) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("baseUrl", appProperties.getLlm().getBaseUrl());
        summary.put("url", chatCompletionsUri().toString());
        summary.put("model", appProperties.getLlm().getModel());
        summary.put("stream", stream);
        summary.put("timeoutSeconds", appProperties.getLlm().getTimeoutSeconds());
        summary.put("messageCount", messages == null ? 0 : messages.size());
        return summary;
    }

    private String exceptionSummary(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        String message = throwable.getMessage();
        String summary = throwable.getClass().getName() + (message == null ? "" : ": " + message);
        Throwable cause = throwable.getCause();
        if (cause != null && cause != throwable) {
            summary += "; cause=" + exceptionSummary(cause);
        }
        return sanitize(summary);
    }

    private String toJson(Map<String, Object> summary) {
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (Exception ex) {
            return summary.toString();
        }
    }
}
