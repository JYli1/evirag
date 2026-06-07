package com.evirag.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.evirag.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

/**
 * OpenAI 兼容聊天模型客户端测试。
 *
 * <p>测试使用本地 MockWebServer，不访问真实 OpenAI 或硅基流动服务，只验证请求格式、响应解析和错误摘要。</p>
 */
class OpenAiCompatibleLlmClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sendsChatCompletionRequestAndParsesAnswer() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(json(200, """
                    {"choices":[{"message":{"content":"基于知识库的回答"}}]}
                    """));
            server.start();

            OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(appProperties(server), objectMapper);

            String answer = client.complete(List.of(LlmMessage.user("请回答")));

            assertThat(answer).isEqualTo("基于知识库的回答");
            RecordedRequest request = server.takeRequest();
            assertThat(request.getPath()).isEqualTo("/v1/chat/completions");
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-llm-key");
            JsonNode body = objectMapper.readTree(request.getBody().readUtf8());
            assertThat(body.path("model").asText()).isEqualTo("gpt-compatible");
            assertThat(body.path("stream").asBoolean()).isFalse();
            assertThat(body.path("messages").get(0).path("content").asText()).isEqualTo("请回答");
        }
    }

    @Test
    void parsesOpenAiStyleStreamingDeltas() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("""
                            data: {"choices":[{"delta":{"content":"你"}}]}

                            data: {"choices":[{"delta":{"content":"好"}}]}

                            data: [DONE]

                            """));
            server.start();

            OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(appProperties(server), objectMapper);
            List<String> deltas = new ArrayList<>();

            client.stream(List.of(LlmMessage.user("打招呼")), deltas::add);

            assertThat(String.join("", deltas)).isEqualTo("你好");
            RecordedRequest request = server.takeRequest();
            JsonNode body = objectMapper.readTree(request.getBody().readUtf8());
            assertThat(body.path("stream").asBoolean()).isTrue();
        }
    }

    @Test
    void exposesLlmStageAndRawHttpError() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(json(401, "{\"error\":\"invalid api key\"}"));
            server.start();

            OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(appProperties(server), objectMapper);

            assertThatThrownBy(() -> client.complete(List.of(LlmMessage.user("请回答"))))
                    .isInstanceOf(LlmException.class)
                    .satisfies(ex -> {
                        LlmException llmException = (LlmException) ex;
                        assertThat(llmException.getStage()).isEqualTo("LLM");
                        assertThat(llmException.getRawSummary()).contains("HTTP 401");
                    });
        }
    }

    private AppProperties appProperties(MockWebServer server) {
        AppProperties properties = new AppProperties();
        properties.getLlm().setBaseUrl(server.url("/v1").toString());
        properties.getLlm().setApiKey("test-llm-key");
        properties.getLlm().setModel("gpt-compatible");
        properties.getLlm().setTimeoutSeconds(5);
        return properties;
    }

    private MockResponse json(int statusCode, String body) {
        return new MockResponse()
                .setResponseCode(statusCode)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
