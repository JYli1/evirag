package com.evirag.embedding;

import com.evirag.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * OpenAI 兼容 Embedding 客户端。
 *
 * <p>硅基流动 embedding 接口兼容 OpenAI `/v1/embeddings` 格式，因此这里按通用 OpenAI 请求体构造：
 * `model` 指定模型，`input` 传入文本数组，返回的 `data[].embedding` 作为向量结果。</p>
 */
@Component
public class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleEmbeddingClient(AppProperties appProperties, ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(appProperties.getEmbedding().getTimeoutSeconds()))
                .build();
    }

    @Override
    public List<List<Double>> embed(List<String> inputs) {
        validateRequest(inputs);
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", appProperties.getEmbedding().getModel());
            body.put("input", inputs);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(embeddingUri())
                    .timeout(Duration.ofSeconds(appProperties.getEmbedding().getTimeoutSeconds()))
                    .header("Authorization", "Bearer " + appProperties.getEmbedding().getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new EmbeddingException("HTTP " + response.statusCode() + ": " + sanitize(response.body()));
            }
            return parseEmbeddings(response.body(), inputs.size());
        } catch (EmbeddingException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new EmbeddingException(sanitize(ex), ex);
        }
    }

    private void validateRequest(List<String> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw new EmbeddingException("Embedding 输入不能为空");
        }
        if (appProperties.getEmbedding().getBaseUrl() == null || appProperties.getEmbedding().getBaseUrl().isBlank()) {
            throw new EmbeddingException("EMBEDDING_BASE_URL 不能为空");
        }
        if (appProperties.getEmbedding().getApiKey() == null || appProperties.getEmbedding().getApiKey().isBlank()) {
            throw new EmbeddingException("EMBEDDING_API_KEY 不能为空");
        }
        if (appProperties.getEmbedding().getModel() == null || appProperties.getEmbedding().getModel().isBlank()) {
            throw new EmbeddingException("EMBEDDING_MODEL 不能为空");
        }
    }

    private URI embeddingUri() {
        String baseUrl = appProperties.getEmbedding().getBaseUrl().replaceAll("/+$", "");
        return URI.create(baseUrl + "/embeddings");
    }

    /**
     * OpenAI 兼容响应通常包含 index 字段；如果存在就按 index 排序，确保输出顺序与输入顺序一致。
     */
    private List<List<Double>> parseEmbeddings(String body, int expectedSize) throws Exception {
        JsonNode data = objectMapper.readTree(body).path("data");
        if (!data.isArray() || data.size() != expectedSize) {
            throw new EmbeddingException("Embedding 响应数量不匹配：" + sanitize(body));
        }

        List<EmbeddingItem> items = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            JsonNode item = data.get(i);
            JsonNode embeddingNode = item.path("embedding");
            if (!embeddingNode.isArray()) {
                throw new EmbeddingException("Embedding 响应缺少 embedding 数组：" + sanitize(body));
            }
            List<Double> vector = new ArrayList<>();
            for (JsonNode value : embeddingNode) {
                vector.add(value.asDouble());
            }
            items.add(new EmbeddingItem(item.path("index").asInt(i), vector));
        }
        return items.stream()
                .sorted(Comparator.comparingInt(EmbeddingItem::index))
                .map(EmbeddingItem::embedding)
                .toList();
    }

    private String sanitize(Exception exception) {
        return sanitize(exception.getClass().getSimpleName() + ": " + exception.getMessage());
    }

    private String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("(?i)(api[_-]?key|secret|token|password|authorization)=\\S+", "$1=***");
    }

    private record EmbeddingItem(int index, List<Double> embedding) {
    }
}
