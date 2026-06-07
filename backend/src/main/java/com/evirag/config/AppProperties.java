package com.evirag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * EviRAG 应用自有配置。
 *
 * <p>所有字段都映射自 {@code application.yml} 中的 {@code evirag.*} 命名空间。
 * DotenvBootstrap 只负责把 backend/.env 注入 JVM 系统属性；真正的类型转换、默认值解析和配置绑定由 Spring Boot 完成。</p>
 */
@ConfigurationProperties(prefix = "evirag")
public class AppProperties {

    /**
     * 本地上传文件保存目录；生产环境应配置到持久化磁盘或对象存储挂载目录。
     */
    private String uploadDir;

    /**
     * 单个上传文件允许的最大大小，单位为 MB；Controller 校验和上传组件应统一引用该值。
     */
    private int maxFileSizeMb;

    /**
     * 邮件业务配置；SMTP 连接参数仍由 Spring Boot 的 spring.mail.* 管理。
     */
    private Mail mail = new Mail();

    /**
     * JWT 签名与过期配置；后续认证任务应基于该对象集中校验弱密钥。
     */
    private Jwt jwt = new Jwt();

    /**
     * 大语言模型接口配置；API Key 不应写入注释、日志或默认状态输出。
     */
    private ModelEndpoint llm = new ModelEndpoint();

    /**
     * Embedding 模型接口配置；通常使用 OpenAI 兼容接口。
     */
    private ModelEndpoint embedding = new ModelEndpoint();

    /**
     * Chroma 向量库连接配置；知识库向量集合名称会基于 collectionPrefix 生成。
     */
    private Chroma chroma = new Chroma();

    /**
     * RAG 检索与上下文拼装配置。
     */
    private Rag rag = new Rag();

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

    public Rag getRag() {
        return rag;
    }

    public void setRag(Rag rag) {
        this.rag = rag;
    }

    /**
     * 邮件业务身份配置。
     */
    public static class Mail {

        /**
         * 系统邮件发件人地址；为空时邮件发送模块应在启动校验或发送前给出明确错误。
         */
        private String from;

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
        private String secret;

        /**
         * JWT 过期时间，单位为分钟。
         */
        private long expireMinutes;

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
        private String baseUrl;

        /**
         * 模型服务访问令牌；不得写入日志、错误响应或健康检查明文输出。
         */
        private String apiKey;

        /**
         * 模型名称；LLM 与 Embedding 会分别绑定各自的模型。
         */
        private String model;

        /**
         * HTTP 请求超时时间，单位为秒。
         */
        private int timeoutSeconds;

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
        private String host;

        /**
         * Chroma HTTP 服务端口。
         */
        private int port;

        /**
         * 知识库向量集合名前缀，用于避免与其他项目集合冲突。
         */
        private String collectionPrefix;

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

        public String getCollectionPrefix() {
            return collectionPrefix;
        }

        public void setCollectionPrefix(String collectionPrefix) {
            this.collectionPrefix = collectionPrefix;
        }
    }

    /**
     * RAG 检索参数配置。
     */
    public static class Rag {

        /**
         * 每次问题检索返回的候选文本块数量。
         */
        private int topK;

        /**
         * 低相似度阈值；低于该阈值的召回结果可被标记为弱相关或拒答依据。
         */
        private double lowScoreThreshold;

        /**
         * 多轮对话拼装提示词时保留的历史轮数。
         */
        private int historyTurns;

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
}
