package com.evirag.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * EviRAG 应用自有配置。
 *
 * <p>所有字段都映射自 {@code application.yml} 中的 {@code evirag.*} 命名空间。
 * DotenvBootstrap 只负责把 backend/.env 注入 JVM 系统属性；真正的类型转换、默认值解析和配置绑定由 Spring Boot 完成。</p>
 */
@Validated
@ConfigurationProperties(prefix = "evirag")
public class AppProperties {

    // 下面这些字段和 application.yml 的 evirag.* 一一对应；Spring 会通过 setter 自动注入配置值。

    /**
     * 本地上传文件保存目录；生产环境应配置到持久化磁盘或对象存储挂载目录。
     */
    @NotBlank
    private String uploadDir = "./uploads";

    /**
     * 单个上传文件允许的最大大小，单位为 MB；Controller 校验和上传组件应统一引用该值。
     */
    @Positive
    private int maxFileSizeMb = 20;

    /**
     * 邮件业务配置；SMTP 连接参数仍由 Spring Boot 的 spring.mail.* 管理。
     */
    @Valid
    private Mail mail = new Mail();

    /**
     * JWT 签名与过期配置；后续认证任务应基于该对象集中校验弱密钥。
     */
    @Valid
    private Jwt jwt = new Jwt();

    /**
     * 大语言模型接口配置；API Key 不应写入注释、日志或默认状态输出。
     */
    @Valid
    private ModelEndpoint llm = new ModelEndpoint();

    /**
     * Embedding 模型接口配置；通常使用 OpenAI 兼容接口。
     */
    @Valid
    private ModelEndpoint embedding = new ModelEndpoint();

    /**
     * Chroma 向量库连接配置；知识库向量集合名称会基于 collectionPrefix 生成。
     */
    @Valid
    private Chroma chroma = new Chroma();

    /**
     * 文档切片配置。
     */
    @Valid
    private Chunk chunk = new Chunk();

    /**
     * RAG 检索与上下文拼装配置。
     */
    @Valid
    private Rag rag = new Rag();

    /**
     * Tavily 联网搜索配置；仅当用户在前端开启搜索时才会调用。
     */
    @Valid
    private WebSearch webSearch = new WebSearch();

    public String getUploadDir() {
        return uploadDir;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public int getMaxFileSizeMb() {
        return maxFileSizeMb;
    }

    public void setMaxFileSizeMb(int maxFileSizeMb) {
        this.maxFileSizeMb = maxFileSizeMb;
    }

    public Mail getMail() {
        return mail;
    }

    public void setMail(Mail mail) {
        this.mail = mail;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    public ModelEndpoint getLlm() {
        return llm;
    }

    public void setLlm(ModelEndpoint llm) {
        this.llm = llm;
    }

    public ModelEndpoint getEmbedding() {
        return embedding;
    }

    public void setEmbedding(ModelEndpoint embedding) {
        this.embedding = embedding;
    }

    public Chroma getChroma() {
        return chroma;
    }

    public void setChroma(Chroma chroma) {
        this.chroma = chroma;
    }

    public Chunk getChunk() {
        return chunk;
    }

    public void setChunk(Chunk chunk) {
        this.chunk = chunk;
    }

    public Rag getRag() {
        return rag;
    }

    public void setRag(Rag rag) {
        this.rag = rag;
    }

    public WebSearch getWebSearch() {
        return webSearch;
    }

    public void setWebSearch(WebSearch webSearch) {
        this.webSearch = webSearch;
    }

    /**
     * 邮件业务身份配置。
     */
    public static class Mail {

        /**
         * 系统邮件发件人地址；为空时邮件发送模块应在启动校验或发送前给出明确错误。
         */
        private String from = "";

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }
    }

    /**
     * JWT 配置。
     */
    public static class Jwt {

        /**
         * JWT 签名密钥；当前只做绑定，后续认证任务应拒绝空值和弱默认值。
         */
        private String secret = "change-me";

        /**
         * JWT 过期时间，单位为分钟。
         */
        @Positive
        private long expireMinutes = 1440;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getExpireMinutes() {
            return expireMinutes;
        }

        public void setExpireMinutes(long expireMinutes) {
            this.expireMinutes = expireMinutes;
        }

        /**
         * 判断当前密钥是否明显不适合生产使用；Task 3 可直接复用该判断做启动校验。
         */
        public boolean hasWeakSecret() {
            return secret == null || secret.isBlank() || "change-me".equals(secret);
        }
    }

    /**
     * OpenAI 兼容模型接口配置，供 LLM 和 Embedding 共用。
     */
    public static class ModelEndpoint {

        /**
         * OpenAI 兼容 API 基础地址，例如 https://api.openai.com/v1。
         */
        private String baseUrl = "";

        /**
         * 模型服务访问令牌；不得写入日志、错误响应或健康检查明文输出。
         */
        private String apiKey = "";

        /**
         * 模型名称；LLM 与 Embedding 会分别绑定各自的模型。
         */
        private String model = "";

        /**
         * HTTP 请求超时时间，单位为秒。
         */
        @Positive
        private int timeoutSeconds = 60;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    /**
     * Chroma 向量数据库连接配置。
     */
    public static class Chroma {

        /**
         * Chroma 服务主机名或 IP。
         */
        @NotBlank
        private String host = "localhost";

        /**
         * Chroma HTTP 服务端口。
         */
        @Positive
        @Max(65535)
        private int port = 8000;

        /**
         * Chroma 租户；本地 Chroma 默认是 default_tenant。
         */
        @NotBlank
        private String tenant = "default_tenant";

        /**
         * Chroma 数据库；本地 Chroma 默认是 default_database。
         */
        @NotBlank
        private String database = "default_database";

        /**
         * Chroma 访问令牌；本地无鉴权部署可留空，启用鉴权或使用云服务时通过 .env 配置。
         */
        private String token = "";

        /**
         * 知识库向量集合名前缀，用于避免与其他项目集合冲突。
         */
        @NotBlank
        private String collectionPrefix = "rag_kb_";

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getTenant() {
            return tenant;
        }

        public void setTenant(String tenant) {
            this.tenant = tenant;
        }

        public String getDatabase() {
            return database;
        }

        public void setDatabase(String database) {
            this.database = database;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getCollectionPrefix() {
            return collectionPrefix;
        }

        public void setCollectionPrefix(String collectionPrefix) {
            this.collectionPrefix = collectionPrefix;
        }
    }

    /**
     * 文本切片配置。
     */
    public static class Chunk {

        /**
         * 单个切片最大字符数；过长段落会按该窗口继续切分。
         */
        @Positive
        private int maxChars = 1200;

        /**
         * 字符窗口之间的重叠字符数，用于减少切片边界处上下文断裂。
         */
        @PositiveOrZero
        private int overlapChars = 120;

        public int getMaxChars() {
            return maxChars;
        }

        public void setMaxChars(int maxChars) {
            this.maxChars = maxChars;
        }

        public int getOverlapChars() {
            return overlapChars;
        }

        public void setOverlapChars(int overlapChars) {
            this.overlapChars = overlapChars;
        }

        /**
         * 重叠窗口必须小于最大窗口，否则字符切片时无法向前推进。
         */
        @AssertTrue(message = "切片重叠长度必须小于最大长度")
        public boolean isOverlapSmallerThanMax() {
            // 例如 max=1200、overlap=120 时，每次窗口向前移动 1080 个字符；如果 overlap>=max 会死循环。
            return overlapChars < maxChars;
        }
    }

    /**
     * RAG 检索参数配置。
     */
    public static class Rag {

        /**
         * 每次问题检索返回的候选文本块数量。
         */
        @Positive
        private int topK = 5;

        /**
         * 低相似度阈值；低于该阈值的召回结果可被标记为弱相关或拒答依据。
         */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double lowScoreThreshold = 0.35;

        /**
         * 多轮对话拼装提示词时保留的历史轮数。
         */
        @PositiveOrZero
        private int historyTurns = 4;

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public double getLowScoreThreshold() {
            return lowScoreThreshold;
        }

        public void setLowScoreThreshold(double lowScoreThreshold) {
            this.lowScoreThreshold = lowScoreThreshold;
        }

        public int getHistoryTurns() {
            return historyTurns;
        }

        public void setHistoryTurns(int historyTurns) {
            this.historyTurns = historyTurns;
        }
    }

    /**
     * Tavily Search / Extract 配置。
     */
    public static class WebSearch {

        /**
         * 是否允许前端搜索开关触发 Tavily 调用；关闭后用户开启搜索会得到明确错误。
         */
        private boolean enabled = true;

        /**
         * Tavily API 基础地址。
         */
        @NotBlank
        private String baseUrl = "https://api.tavily.com";

        /**
         * Tavily API Key，通过 backend/.env 的 TAVILY_API_KEY 配置。
         */
        private String apiKey = "";

        /**
         * 系统 curl 命令路径；Windows 默认也可用 curl.exe。
         */
        @NotBlank
        private String curlExecutable = "curl";

        /**
         * Search 深度，通常为 basic 或 advanced。
         */
        @NotBlank
        private String searchDepth = "basic";

        /**
         * Extract 深度，通常为 basic 或 advanced。
         */
        @NotBlank
        private String extractDepth = "basic";

        /**
         * Search 最多返回结果数量。
         */
        @Positive
        @Max(10)
        private int maxResults = 5;

        /**
         * curl 单次请求超时时间，单位为秒。
         */
        @Positive
        private int timeoutSeconds = 25;

        /**
         * 每条网页资料进入 prompt 前的最大字符数。
         */
        @Positive
        private int perSourceMaxChars = 1200;

        /**
         * 联网资料整体进入 prompt 的最大字符数。
         */
        @Positive
        private int contextMaxChars = 6000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getCurlExecutable() {
            return curlExecutable;
        }

        public void setCurlExecutable(String curlExecutable) {
            this.curlExecutable = curlExecutable;
        }

        public String getSearchDepth() {
            return searchDepth;
        }

        public void setSearchDepth(String searchDepth) {
            this.searchDepth = searchDepth;
        }

        public String getExtractDepth() {
            return extractDepth;
        }

        public void setExtractDepth(String extractDepth) {
            this.extractDepth = extractDepth;
        }

        public int getMaxResults() {
            return maxResults;
        }

        public void setMaxResults(int maxResults) {
            this.maxResults = maxResults;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public int getPerSourceMaxChars() {
            return perSourceMaxChars;
        }

        public void setPerSourceMaxChars(int perSourceMaxChars) {
            this.perSourceMaxChars = perSourceMaxChars;
        }

        public int getContextMaxChars() {
            return contextMaxChars;
        }

        public void setContextMaxChars(int contextMaxChars) {
            this.contextMaxChars = contextMaxChars;
        }
    }
}
