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
            // Embedding 可以一次传入多段文本。批量请求比逐段请求更快，也能减少外部 API 调用次数。
            body.put("model", appProperties.getEmbedding().getModel());
            body.put("input", inputs);

            // 这里同样使用 OpenAI-compatible 协议，最终地址为 EMBEDDING_BASE_URL + /embeddings。
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
            // 返回值必须和 inputs 一一对应，后续会按同一个下标把向量写回对应的文档切片。
            return parseEmbeddings(response.body(), inputs.size());
        } catch (EmbeddingException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new EmbeddingException(sanitize(ex), ex);
        }
    }

    private void validateRequest(List<String> inputs) {
        // 配置错误是最常见的 Embedding 问题，提前校验能把错误定位到 .env，而不是让索引流程静默失败。
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
        // baseUrl 既可以写成 https://xxx/v1，也可以写成 https://xxx/v1/，这里统一成无尾斜杠再拼路径。
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
            // 每个 embedding 是一组 double 数字，后续 Chroma 用这些数字计算相似度。
            List<Double> vector = new ArrayList<>();
            for (JsonNode value : embeddingNode) {
                vector.add(value.asDouble());
            }
            // 大多数供应商会返回 index 字段；如果没有，就使用当前循环下标兜底。
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
        // 供应商错误响应可能会回显请求信息，输出前统一做一次敏感字段脱敏。
        return raw.replaceAll("(?i)(api[_-]?key|secret|token|password|authorization)=\\S+", "$1=***");
    }

    /**
     * 暂存单个向量和它对应的输入下标，方便最后恢复成与请求输入一致的顺序。
     */
    private record EmbeddingItem(int index, List<Double> embedding) {
    }
}
