package com.evirag.websearch;

import com.evirag.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Tavily Search / Extract 服务。
 *
 * <p>按用户要求，这里不使用 Java HTTP Client，而是用 {@link ProcessBuilder}
 * 调用系统 curl 命令。命令参数逐项传入，不经过 shell 拼接，避免引号和注入问题。</p>
 */
@Service
public class TavilyWebSearchService {

    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s\"'<>\\u3000\\uff0c\\u3002\\uff1b\\uff01\\uff1f）)]+");
    private static final String STATUS_MARKER = "\n__HTTP_STATUS__:";

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public TavilyWebSearchService(AppProperties appProperties, ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 根据用户问题构造联网搜索上下文。
     *
     * <p>如果问题中包含 URL，先调用 Tavily Extract 抽取网页正文，再继续调用 Tavily Search；
     * 如果没有 URL，则直接调用 Tavily Search。</p>
     */
    public WebSearchContext buildContext(String query, WebSearchLogSink logSink) {
        AppProperties.WebSearch config = appProperties.getWebSearch();
        validateConfig(config);
        List<String> urls = extractUrls(query);
        List<String> sections = new ArrayList<>();
        List<String> extractSections = List.of();
        int extractCount = 0;

        if (!urls.isEmpty()) {
            JsonNode extractResponse = postJson("/extract", extractBody(urls, config), "POST /extract", logSink);
            extractSections = extractSections(extractResponse, config);
            extractCount = extractSections.size();
            if (!extractSections.isEmpty()) {
                sections.add("一、用户提供 URL 的网页抽取内容\n" + String.join("\n\n", extractSections));
            }
        }

        JsonNode searchResponse = postJson("/search", searchBody(searchQuery(query, extractSections, config), config), "POST /search", logSink);
        List<String> searchSections = searchSections(searchResponse, config);
        if (!searchSections.isEmpty()) {
            sections.add("二、Tavily Search 搜索结果\n" + String.join("\n\n", searchSections));
        }

        if (sections.isEmpty()) {
            return new WebSearchContext("【联网搜索资料】\nTavily 没有返回可用网页资料。", false, !urls.isEmpty(), 0, 0);
        }
        String prompt = "【联网搜索资料】\n" + truncate(String.join("\n\n", sections), config.getContextMaxChars());
        return new WebSearchContext(prompt, true, !urls.isEmpty(), searchSections.size(), extractCount);
    }

    public List<String> extractUrls(String text) {
        Set<String> urls = new LinkedHashSet<>();
        Matcher matcher = URL_PATTERN.matcher(text == null ? "" : text);
        while (matcher.find()) {
            urls.add(trimTrailingPunctuation(matcher.group()));
        }
        return List.copyOf(urls);
    }

    private void validateConfig(AppProperties.WebSearch config) {
        if (!config.isEnabled()) {
            throw new TavilyException("Tavily 搜索功能未启用，请检查 TAVILY_ENABLED 配置。");
        }
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new TavilyException("TAVILY_API_KEY 不能为空，开启搜索前请先在 backend/.env 配置 Tavily API Key。");
        }
    }

    private ObjectNode extractBody(List<String> urls, AppProperties.WebSearch config) {
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode array = body.putArray("urls");
        urls.forEach(array::add);
        body.put("extract_depth", config.getExtractDepth());
        body.put("include_images", false);
        return body;
    }

    private ObjectNode searchBody(String query, AppProperties.WebSearch config) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("query", query == null || query.isBlank() ? "请根据用户提供的信息进行联网搜索" : query);
        body.put("search_depth", config.getSearchDepth());
        body.put("max_results", config.getMaxResults());
        body.put("include_answer", true);
        body.put("include_raw_content", false);
        body.put("include_images", false);
        return body;
    }

    private JsonNode postJson(String path, ObjectNode body, String title, WebSearchLogSink logSink) {
        AppProperties.WebSearch config = appProperties.getWebSearch();
        String endpoint = config.getBaseUrl().replaceAll("/+$", "") + path;
        String requestBody = body.toString();
        if (logSink != null) {
            logSink.log("BACKEND->TAVILY", title, requestBody);
        }

        Path requestFile = writeRequestBody(requestBody);
        String output;
        try {
            List<String> command = List.of(
                    config.getCurlExecutable(),
                    "--silent",
                    "--show-error",
                    "--location",
                    "--request", "POST",
                    endpoint,
                    "--header", "Content-Type: application/json",
                    "--header", "Authorization: Bearer " + config.getApiKey(),
                    "--max-time", String.valueOf(config.getTimeoutSeconds()),
                    "--data-binary", "@" + requestFile.toAbsolutePath(),
                    "--write-out", STATUS_MARKER + "%{http_code}"
            );
            output = runCurl(command, Duration.ofSeconds(config.getTimeoutSeconds() + 5L));
        } finally {
            deleteQuietly(requestFile);
        }
        CurlResponse response = parseCurlResponse(output);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new TavilyException(
                    "WEB_SEARCH",
                    "Tavily 请求失败",
                    "Tavily HTTP " + response.statusCode() + ": " + response.body(),
                    null
            );
        }
        if (logSink != null) {
            logSink.log("TAVILY->BACKEND", title + " completed", response.body());
        }
        try {
            return objectMapper.readTree(response.body());
        } catch (IOException ex) {
            throw new TavilyException("Tavily 返回内容不是合法 JSON", ex);
        }
    }

    private Path writeRequestBody(String requestBody) {
        try {
            Path requestFile = Files.createTempFile("evirag-tavily-", ".json");
            Files.writeString(requestFile, requestBody, StandardCharsets.UTF_8);
            return requestFile;
        } catch (IOException ex) {
            throw new TavilyException("写入 Tavily 临时请求体失败。", ex);
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 临时文件清理失败不影响本次搜索结果，下一次系统临时目录清理会处理。
        }
    }

    private String runCurl(List<String> command, Duration timeout) {
        Process process;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            process = builder.start();
        } catch (IOException ex) {
            throw new TavilyException("无法启动 curl 命令，请确认 TAVILY_CURL_EXECUTABLE 配置可用。", ex);
        }
        CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw new CompletionException(ex);
            }
        });
        try {
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                outputFuture.cancel(true);
                throw new TavilyException("Tavily curl 请求超时。");
            }
            String output = readCurlOutput(outputFuture);
            if (process.exitValue() != 0) {
                throw new TavilyException(
                        "WEB_SEARCH",
                        "curl 命令执行失败",
                        "curl exit=" + process.exitValue() + ": " + output,
                        null
                );
            }
            return output;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new TavilyException("Tavily curl 请求被中断。", ex);
        }
    }

    private String readCurlOutput(CompletableFuture<String> outputFuture) {
        try {
            return outputFuture.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new TavilyException("读取 curl 输出被中断。", ex);
        } catch (ExecutionException ex) {
            throw new TavilyException("读取 curl 输出失败。", ex.getCause() == null ? ex : ex.getCause());
        } catch (TimeoutException ex) {
            throw new TavilyException("读取 curl 输出超时。", ex);
        }
    }

    private CurlResponse parseCurlResponse(String output) {
        int markerIndex = output.lastIndexOf(STATUS_MARKER);
        if (markerIndex < 0) {
            throw new TavilyException("curl 输出中缺少 HTTP 状态码：" + output);
        }
        String body = output.substring(0, markerIndex);
        String status = output.substring(markerIndex + STATUS_MARKER.length()).trim();
        try {
            return new CurlResponse(Integer.parseInt(status), body);
        } catch (NumberFormatException ex) {
            throw new TavilyException("curl HTTP 状态码解析失败：" + status, ex);
        }
    }

    private List<String> extractSections(JsonNode root, AppProperties.WebSearch config) {
        List<String> sections = new ArrayList<>();
        JsonNode results = root.path("results");
        if (!results.isArray()) {
            return sections;
        }
        int index = 1;
        for (JsonNode item : results) {
            String content = firstText(item, "raw_content", "content", "text");
            if (content.isBlank()) {
                continue;
            }
            String url = item.path("url").asText("");
            sections.add("[WEB-E" + index + "] URL=" + url + "\n" + truncate(content, config.getPerSourceMaxChars()));
            index++;
        }
        return sections;
    }

    private List<String> searchSections(JsonNode root, AppProperties.WebSearch config) {
        List<String> sections = new ArrayList<>();
        String answer = root.path("answer").asText("");
        if (!answer.isBlank()) {
            sections.add("[WEB-A] Tavily 汇总答案\n" + truncate(answer, config.getPerSourceMaxChars()));
        }
        JsonNode results = root.path("results");
        if (!results.isArray()) {
            return sections;
        }
        int index = 1;
        for (JsonNode item : results) {
            String content = firstText(item, "content", "raw_content", "snippet");
            if (content.isBlank()) {
                continue;
            }
            String title = item.path("title").asText("未命名网页");
            String url = item.path("url").asText("");
            String score = item.has("score") ? " 分数=" + item.path("score").asText() : "";
            sections.add("[WEB-S" + index + "] 标题=" + title + " URL=" + url + score
                    + "\n" + truncate(content, config.getPerSourceMaxChars()));
            index++;
        }
        return sections;
    }

    private String firstText(JsonNode node, String... names) {
        for (String name : names) {
            String value = node.path(name).asText("");
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String queryWithoutUrls(String query) {
        String cleaned = URL_PATTERN.matcher(query == null ? "" : query).replaceAll(" ").replaceAll("\\s+", " ").trim();
        return cleaned;
    }

    private String searchQuery(String query, List<String> extractSections, AppProperties.WebSearch config) {
        String cleanedQuery = queryWithoutUrls(query);
        if (extractSections == null || extractSections.isEmpty()) {
            return cleanedQuery;
        }
        StringBuilder builder = new StringBuilder();
        builder.append(cleanedQuery == null || cleanedQuery.isBlank()
                ? "请根据用户提供 URL 的网页抽取内容继续联网搜索相关资料"
                : cleanedQuery);
        builder.append("\n\n用户提供 URL 的网页抽取摘要：\n");
        for (String section : extractSections) {
            builder.append("- ")
                    .append(truncate(section.replaceAll("\\s+", " "), Math.min(config.getPerSourceMaxChars(), 600)))
                    .append("\n");
        }
        return truncate(builder.toString(), Math.min(config.getContextMaxChars(), 3000));
    }

    private String trimTrailingPunctuation(String url) {
        return url.replaceAll("[,.;!?，。；！？]+$", "");
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength) + "...[truncated]";
    }

    private record CurlResponse(int statusCode, String body) {
    }
}
