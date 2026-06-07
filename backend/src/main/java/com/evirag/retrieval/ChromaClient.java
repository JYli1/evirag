package com.evirag.retrieval;

import com.evirag.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Chroma HTTP 客户端。
 *
 * <p>当前实现以 Chroma v2 REST API 为目标，负责创建集合、写入向量、检索 Top-K 和按文档删除向量。
 * Chroma 只作为检索索引，业务主数据仍以 MySQL 的 document_chunks 为准。</p>
 */
@Component
public class ChromaClient {

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Map<String, String> collectionIds = new ConcurrentHashMap<>();

    public ChromaClient(AppProperties appProperties, ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 创建或复用 Chroma collection。
     */
    public void ensureCollection(String collectionName) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("name", collectionName);
            body.put("get_or_create", true);
            body.put("metadata", Map.of("project", "EviRAG"));

            HttpResponse<String> response = sendJson(
                    "POST",
                    "/collections",
                    body,
                    Duration.ofSeconds(10)
            );
            if (response.statusCode() == 409 || isAlreadyExists(response)) {
                collectionIds.put(collectionName, fetchCollectionId(collectionName));
                return;
            }
            requireSuccess(response, "创建 Chroma collection 失败");
            JsonNode root = objectMapper.readTree(response.body());
            String id = root.path("id").asText(collectionName);
            collectionIds.put(collectionName, id.isBlank() ? collectionName : id);
        } catch (ChromaException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ChromaException(sanitize(ex), ex);
        }
    }

    /**
     * 批量写入或更新向量。
     */
    public void upsert(String collectionName, List<ChromaVector> vectors) {
        if (vectors == null || vectors.isEmpty()) {
            return;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ids", vectors.stream().map(ChromaVector::id).toList());
            body.put("embeddings", vectors.stream().map(ChromaVector::embedding).toList());
            body.put("documents", vectors.stream().map(ChromaVector::document).toList());
            body.put("metadatas", vectors.stream().map(ChromaVector::metadata).toList());

            HttpResponse<String> response = sendJson(
                    "POST",
                    "/collections/" + encode(collectionKey(collectionName)) + "/upsert",
                    body,
                    Duration.ofSeconds(30)
            );
            requireSuccess(response, "写入 Chroma 向量失败");
        } catch (ChromaException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ChromaException(sanitize(ex), ex);
        }
    }

    /**
     * 按知识库和附加过滤条件查询 Top-K 片段。
     */
    public List<ChromaQueryResult> query(
            String collectionName,
            List<Double> embedding,
            int topK,
            Map<String, Object> where
    ) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("query_embeddings", List.of(embedding));
            body.put("n_results", topK);
            body.put("where", where == null ? Map.of() : where);
            body.put("include", List.of("documents", "metadatas", "distances"));

            HttpResponse<String> response = sendJson(
                    "POST",
                    "/collections/" + encode(collectionKey(collectionName)) + "/query",
                    body,
                    Duration.ofSeconds(30)
            );
            requireSuccess(response, "查询 Chroma 向量失败");
            return parseQueryResults(response.body());
        } catch (ChromaException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ChromaException(sanitize(ex), ex);
        }
    }

    /**
     * 删除指定文档在 Chroma 中的所有向量。
     */
    public void deleteByDocumentId(String collectionName, Long documentId) {
        try {
            Map<String, Object> body = Map.of("where", Map.of("document_id", documentId));
            HttpResponse<String> response = sendJson(
                    "POST",
                    "/collections/" + encode(collectionKey(collectionName)) + "/delete",
                    body,
                    Duration.ofSeconds(30)
            );
            requireSuccess(response, "删除 Chroma 文档向量失败");
        } catch (ChromaException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ChromaException(sanitize(ex), ex);
        }
    }

    private HttpResponse<String> sendJson(String method, String path, Object body, Duration timeout) throws Exception {
        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.ofString(
                objectMapper.writeValueAsString(body),
                StandardCharsets.UTF_8
        );
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(databaseBaseUrl() + path))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .method(method, publisher);
        addChromaToken(builder);
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> sendGet(String path, Duration timeout) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(databaseBaseUrl() + path))
                .timeout(timeout)
                .GET();
        addChromaToken(builder);
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String fetchCollectionId(String collectionName) throws Exception {
        HttpResponse<String> response = sendGet("/collections/" + encode(collectionName), Duration.ofSeconds(10));
        requireSuccess(response, "获取 Chroma collection 失败");
        JsonNode root = objectMapper.readTree(response.body());
        String id = root.path("id").asText(collectionName);
        return id.isBlank() ? collectionName : id;
    }

    private void addChromaToken(HttpRequest.Builder builder) {
        String token = appProperties.getChroma().getToken();
        if (token != null && !token.isBlank()) {
            builder.header("x-chroma-token", token);
        }
    }

    private void requireSuccess(HttpResponse<String> response, String message) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ChromaException(message + "：HTTP " + response.statusCode() + ": " + sanitize(response.body()));
        }
    }

    private boolean isAlreadyExists(HttpResponse<String> response) {
        String body = response.body() == null ? "" : response.body().toLowerCase();
        return response.statusCode() == 400 && body.contains("already");
    }

    private String collectionKey(String collectionName) throws Exception {
        String cached = collectionIds.get(collectionName);
        if (cached != null && !cached.isBlank()) {
            return cached;
        }
        String id = fetchCollectionId(collectionName);
        collectionIds.put(collectionName, id);
        return id;
    }

    private String databaseBaseUrl() {
        String host = appProperties.getChroma().getHost();
        String base = host.startsWith("http://") || host.startsWith("https://")
                ? host.replaceAll("/+$", "")
                : "http://" + host + ":" + appProperties.getChroma().getPort();
        return base
                + "/api/v2/tenants/"
                + encode(appProperties.getChroma().getTenant())
                + "/databases/"
                + encode(appProperties.getChroma().getDatabase());
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private List<ChromaQueryResult> parseQueryResults(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode ids = root.path("ids").path(0);
        JsonNode documents = root.path("documents").path(0);
        JsonNode metadatas = root.path("metadatas").path(0);
        JsonNode distances = root.path("distances").path(0);
        List<ChromaQueryResult> results = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            double distance = distances.path(i).asDouble(Double.NaN);
            double score = Double.isNaN(distance) ? 0.0 : Math.max(0.0, 1.0 - distance);
            results.add(new ChromaQueryResult(
                    ids.path(i).asText(),
                    documents.path(i).asText(),
                    score,
                    objectMapper.convertValue(metadatas.path(i), Map.class)
            ));
        }
        return results;
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

    /**
     * 待写入 Chroma 的向量记录。
     */
    public record ChromaVector(
            String id,
            List<Double> embedding,
            String document,
            Map<String, Object> metadata
    ) {
    }

    /**
     * Chroma 查询结果，score 用于前端展示相似度和低相关性标记。
     */
    public record ChromaQueryResult(
            String id,
            String document,
            double score,
            Map<String, Object> metadata
    ) {
    }
}
