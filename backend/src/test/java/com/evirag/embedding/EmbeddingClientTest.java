package com.evirag.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.evirag.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

/**
 * OpenAI 兼容 embedding 客户端测试。
 *
 * <p>硅基流动使用 OpenAI 兼容请求格式，本测试用 MockWebServer 验证请求体和失败摘要，而不访问真实外部网络。</p>
 */
class EmbeddingClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sendsOpenAiCompatibleEmbeddingRequest() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"data":[{"index":0,"embedding":[0.1,0.2,0.3]}]}
                            """));
            server.start();

            OpenAiCompatibleEmbeddingClient client = new OpenAiCompatibleEmbeddingClient(
                    appProperties(server),
                    objectMapper
            );

            List<List<Double>> vectors = client.embed(List.of("第一段文本"));

            assertThat(vectors).containsExactly(List.of(0.1, 0.2, 0.3));
            RecordedRequest request = server.takeRequest();
            assertThat(request.getPath()).isEqualTo("/v1/embeddings");
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-key");
            JsonNode body = objectMapper.readTree(request.getBody().readUtf8());
            assertThat(body.path("model").asText()).isEqualTo("BAAI/bge-m3");
            assertThat(body.path("input").get(0).asText()).isEqualTo("第一段文本");
        }
    }

    @Test
    void exposesEmbeddingStageAndRawHttpError() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(401)
                    .setBody("{\"error\":\"invalid api key\"}"));
            server.start();

            OpenAiCompatibleEmbeddingClient client = new OpenAiCompatibleEmbeddingClient(
                    appProperties(server),
                    objectMapper
            );

            assertThatThrownBy(() -> client.embed(List.of("文本")))
                    .isInstanceOf(EmbeddingException.class)
                    .satisfies(ex -> {
                        EmbeddingException embeddingException = (EmbeddingException) ex;
                        assertThat(embeddingException.getStage()).isEqualTo("EMBEDDING");
                        assertThat(embeddingException.getRawSummary()).contains("HTTP 401");
                    });
        }
    }

    private AppProperties appProperties(MockWebServer server) {
        AppProperties properties = new AppProperties();
        properties.getEmbedding().setBaseUrl(server.url("/v1").toString());
        properties.getEmbedding().setApiKey("test-key");
        properties.getEmbedding().setModel("BAAI/bge-m3");
        properties.getEmbedding().setTimeoutSeconds(5);
        return properties;
    }
}
