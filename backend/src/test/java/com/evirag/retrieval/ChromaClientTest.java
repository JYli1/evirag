package com.evirag.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import com.evirag.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

/**
 * Chroma HTTP 客户端测试。
 *
 * <p>测试只验证本地构造的 v2 REST 请求，不依赖真实 Chroma 服务；真实联通性留给后续运行手册和集成环境。</p>
 */
class ChromaClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createsCollectionAndUpsertsVectorsWithRequiredMetadata() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(json(200, "{\"id\":\"collection-id\",\"name\":\"kb_1\"}"));
            server.enqueue(json(200, "{}"));
            server.start();

            ChromaClient client = new ChromaClient(appProperties(server), objectMapper);
            client.ensureCollection("kb_1");
            client.upsert("kb_1", List.of(new ChromaClient.ChromaVector(
                    "vector-1",
                    List.of(0.1, 0.2),
                    "证据文本",
                    Map.of(
                            "user_id", 7L,
                            "knowledge_base_id", 9L,
                            "document_id", 11L,
                            "chunk_id", 13L
                    )
            )));

            RecordedRequest createRequest = server.takeRequest();
            assertThat(createRequest.getPath())
                    .isEqualTo("/api/v2/tenants/default_tenant/databases/default_database/collections");
            assertThat(createRequest.getHeader("x-chroma-token")).isEqualTo("chroma-token");

            RecordedRequest upsertRequest = server.takeRequest();
            assertThat(upsertRequest.getPath())
                    .isEqualTo("/api/v2/tenants/default_tenant/databases/default_database/collections/collection-id/upsert");
            JsonNode body = objectMapper.readTree(upsertRequest.getBody().readUtf8());
            JsonNode metadata = body.path("metadatas").get(0);
            assertThat(metadata.path("user_id").asLong()).isEqualTo(7L);
            assertThat(metadata.path("knowledge_base_id").asLong()).isEqualTo(9L);
            assertThat(metadata.path("document_id").asLong()).isEqualTo(11L);
            assertThat(metadata.path("chunk_id").asLong()).isEqualTo(13L);
        }
    }

    @Test
    void queriesTopKAndConvertsDistanceToScore() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(json(200, "{\"id\":\"collection-id\",\"name\":\"kb_1\"}"));
            server.enqueue(json(200, """
                    {
                      "ids":[["vector-1"]],
                      "documents":[["证据文本"]],
                      "metadatas":[[{"chunk_id":13}]],
                      "distances":[[0.25]]
                    }
                    """));
            server.start();

            ChromaClient client = new ChromaClient(appProperties(server), objectMapper);
            client.ensureCollection("kb_1");
            List<ChromaClient.ChromaQueryResult> results = client.query(
                    "kb_1",
                    List.of(0.1, 0.2),
                    5,
                    Map.of("knowledge_base_id", 9L)
            );

            RecordedRequest createRequest = server.takeRequest();
            assertThat(createRequest.getPath()).contains("/collections");
            RecordedRequest queryRequest = server.takeRequest();
            JsonNode body = objectMapper.readTree(queryRequest.getBody().readUtf8());
            List<String> includes = new ArrayList<>();
            body.path("include").forEach(node -> includes.add(node.asText()));
            assertThat(includes).contains("documents", "metadatas", "distances");
            assertThat(results).hasSize(1);
            assertThat(results.get(0).score()).isEqualTo(0.75);
        }
    }

    private AppProperties appProperties(MockWebServer server) {
        AppProperties properties = new AppProperties();
        properties.getChroma().setHost(server.url("/").toString());
        properties.getChroma().setToken("chroma-token");
        return properties;
    }

    private MockResponse json(int statusCode, String body) {
        return new MockResponse()
                .setResponseCode(statusCode)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
