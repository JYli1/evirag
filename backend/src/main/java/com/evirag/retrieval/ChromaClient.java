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
    /**
     * Chroma v2 的 collection 可以用 name 创建，但部分接口更稳定地接受 collection id。
     * 这里缓存 name -> id，减少每次 upsert/query/delete 前都去 Chroma 查询一次。
     */
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
            // get_or_create=true 表示“没有就创建，有就复用”，适合知识库第一次上传文档时自动建索引。
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
                // 不同 Chroma 版本对“已存在”的状态码不完全一致，拿到已存在后再查询一次 id 即可。
                collectionIds.put(collectionName, fetchCollectionId(collectionName));
                return;
            }
            requireSuccess(response, "创建 Chroma collection 失败");
            JsonNode root = objectMapper.readTree(response.body());
            String id = root.path("id").asText(collectionName);
            // 老版本或特殊部署可能不返回 id，此时退回 collectionName，保证后续路径仍可尝试。
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
            // Chroma 的 upsert 是四个数组按下标对齐：ids[i]、embeddings[i]、documents[i]、metadatas[i] 属于同一条记录。
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
            String collectionKey = collectionKey(collectionName);
            Map<String, Object> body = new LinkedHashMap<>();
            // query_embeddings 外面还有一层数组，是因为 Chroma 支持一次查询多个问题；本项目每次只查一个问题。
            body.put("query_embeddings", List.of(embedding));
            body.put("n_results", topK);
            // where 用于限制只查当前用户/知识库的片段，避免不同用户或知识库之间串数据。
            body.put("where", where == null ? Map.of() : where);
            body.put("include", List.of("documents", "metadatas", "distances"));

            HttpResponse<String> response = sendJson(
                    "POST",
                    "/collections/" + encode(collectionKey) + "/query",
                    body,
                    Duration.ofSeconds(30)
            );
            requireSuccess(response, "查询 Chroma 向量失败");
            return parseQueryResults(response.body());
        } catch (ChromaException ex) {
            if (isCollectionNotFound(ex)) {
                // 没有 collection 说明这个知识库还没有成功索引过文档，按“无检索结果”处理即可。
                return List.of();
            }
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
            // 删除文档时只删 Chroma 中该 document_id 的向量，不影响同一知识库里的其他文档。
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
        // Chroma 的写入、查询、删除都通过 JSON 请求体完成，因此统一封装发送逻辑。
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
        // GET 目前主要用于按 collection name 查询 collection id。
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(databaseBaseUrl() + path))
                .timeout(timeout)
                .GET();
        addChromaToken(builder);
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String fetchCollectionId(String collectionName) throws Exception {
        // Chroma v2 的 upsert/query/delete 路径里可以放 collection id，这里通过 name 获取真实 id。
        HttpResponse<String> response = sendGet("/collections/" + encode(collectionName), Duration.ofSeconds(10));
        requireSuccess(response, "获取 Chroma collection 失败");
        JsonNode root = objectMapper.readTree(response.body());
        String id = root.path("id").asText(collectionName);
        return id.isBlank() ? collectionName : id;
    }

    private void addChromaToken(HttpRequest.Builder builder) {
        String token = appProperties.getChroma().getToken();
        if (token != null && !token.isBlank()) {
            // 本地 Chroma 通常不需要 token；如果部署时开启鉴权，就通过配置注入请求头。
            builder.header("x-chroma-token", token);
        }
    }

    private void requireSuccess(HttpResponse<String> response, String message) {
        // 保留 Chroma 原始响应摘要，前端日志可以直接看到 400/404 的具体原因。
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ChromaException(message + "：HTTP " + response.statusCode() + ": " + sanitize(response.body()));
        }
    }

    private boolean isAlreadyExists(HttpResponse<String> response) {
        // 某些 Chroma 版本创建已存在 collection 会返回 400 + already，而不是标准 409。
        String body = response.body() == null ? "" : response.body().toLowerCase();
        return response.statusCode() == 400 && body.contains("already");
    }

    private boolean isCollectionNotFound(ChromaException exception) {
        // 查询阶段 collection 不存在不一定是系统故障，可能只是知识库还没有任何成功索引的文档。
        String rawSummary = exception.getRawSummary() == null ? "" : exception.getRawSummary();
        return rawSummary.contains("HTTP 404") && rawSummary.toLowerCase().contains("collection");
    }

    private String collectionKey(String collectionName) throws Exception {
        // 优先读缓存，缓存没有再访问 Chroma，避免高频聊天时每次检索都多一次网络请求。
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
        // CHROMA_HOST 可以写完整 URL，也可以只写主机名；只写主机名时再拼端口。
        String base = host.startsWith("http://") || host.startsWith("https://")
                ? host.replaceAll("/+$", "")
                : "http://" + host + ":" + appProperties.getChroma().getPort();
        // Chroma v2 REST API 必须带 tenant 和 database，本项目默认值通常是 default_tenant/default_database。
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
        // Chroma 支持批量查询，所以 ids/documents/metadatas/distances 外层第一维是“第几个查询问题”。
        // 本项目每次只查一个问题，因此读取 path(0)。
        JsonNode ids = root.path("ids").path(0);
        JsonNode documents = root.path("documents").path(0);
        JsonNode metadatas = root.path("metadatas").path(0);
        JsonNode distances = root.path("distances").path(0);
        List<ChromaQueryResult> results = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            double distance = distances.path(i).asDouble(Double.NaN);
            // Chroma 返回的是距离，值越小越相似；前端更习惯看分数，所以粗略转换成 0 到 1 的 score。
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
        // Chroma 错误里一般没有 LLM Key，但统一脱敏可以防止代理或网关把敏感头写进错误正文。
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
