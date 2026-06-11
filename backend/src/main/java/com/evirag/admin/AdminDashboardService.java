package com.evirag.admin;

import com.evirag.admin.dto.AdminAuditLogResponse;
import com.evirag.admin.dto.AdminConfigStatusResponse;
import com.evirag.admin.dto.AdminConfigStatusResponse.ConfigStatusItem;
import com.evirag.admin.dto.AdminDashboardResponse;
import com.evirag.admin.dto.AdminUserDetailResponse;
import com.evirag.admin.dto.AdminUserResponse;
import com.evirag.admin.dto.UpdateUserStatusRequest;
import com.evirag.chat.ChatMessage;
import com.evirag.chat.ChatMessageRepository;
import com.evirag.document.DocumentChunkRepository;
import com.evirag.config.AppProperties;
import com.evirag.document.DocumentRepository;
import com.evirag.document.DocumentStatus;
import com.evirag.knowledge.KnowledgeBaseRepository;
import com.evirag.user.User;
import com.evirag.user.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理员面板业务服务。
 *
 * <p>该服务只服务管理员接口，不复用普通用户列表接口，避免管理端为了统计方便绕开普通用户的数据隔离约束。</p>
 */
@Service
public class AdminDashboardService {

    private final UserRepository userRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AdminAuditLogRepository adminAuditLogRepository;
    private final AppProperties appProperties;
    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AdminDashboardService(
            UserRepository userRepository,
            KnowledgeBaseRepository knowledgeBaseRepository,
            DocumentRepository documentRepository,
            DocumentChunkRepository documentChunkRepository,
            ChatMessageRepository chatMessageRepository,
            AdminAuditLogRepository adminAuditLogRepository,
            AppProperties appProperties,
            Environment environment,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.adminAuditLogRepository = adminAuditLogRepository;
        this.appProperties = appProperties;
        this.environment = environment;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 汇总管理员首页需要的核心指标。
     *
     * <p>“问答次数”按用户问题消息数统计；如果后续加入重新生成，可以再细分普通提问和重试提问。</p>
     */
    @Transactional(readOnly = true)
    public AdminDashboardResponse dashboard() {
        return new AdminDashboardResponse(
                // 以下统计都来自 MySQL 主数据，不直接扫 Chroma。
                userRepository.count(),
                userRepository.countByStatus("ACTIVE"),
                userRepository.countByStatus("DISABLED"),
                knowledgeBaseRepository.count(),
                documentRepository.count(),
                documentRepository.countByParseStatus(DocumentStatus.READY),
                documentRepository.countByParseStatus(DocumentStatus.FAILED),
                chatMessageRepository.countByRole(ChatMessage.ROLE_USER),
                documentRepository.countByCreatedAtBetween(todayStart(), tomorrowStart()),
                estimatedTotalTokens(null),
                configStatus().missingCount()
        );
    }

    /**
     * 返回配置是否缺失，不返回配置值本身。
     */
    @Transactional(readOnly = true)
    public AdminConfigStatusResponse configStatus() {
        List<ConfigStatusItem> items = new ArrayList<>();
        // secret=true 的配置只展示“是否配置”，不把真实密钥返回给前端。
        items.add(item("JWT_SECRET", "JWT 签名密钥", "安全", true, true, !appProperties.getJwt().hasWeakSecret()));
        items.add(item("DB_HOST", "数据库地址", "数据库", true, false, hasText(environment.getProperty("DB_HOST"))
                || hasText(environment.getProperty("spring.datasource.url"))));
        items.add(item("DB_USERNAME", "数据库用户名", "数据库", true, true, hasText(environment.getProperty("spring.datasource.username"))));
        items.add(item("DB_PASSWORD", "数据库密码", "数据库", true, true, hasText(environment.getProperty("spring.datasource.password"))));
        items.add(item("MAIL_HOST", "邮箱 SMTP 地址", "邮箱", true, false, hasText(environment.getProperty("spring.mail.host"))));
        items.add(item("MAIL_USERNAME", "邮箱用户名", "邮箱", true, true, hasText(environment.getProperty("spring.mail.username"))));
        items.add(item("MAIL_PASSWORD", "邮箱授权码", "邮箱", true, true, hasText(environment.getProperty("spring.mail.password"))));
        items.add(item("MAIL_FROM", "邮箱发件人", "邮箱", true, false, hasText(appProperties.getMail().getFrom())));
        items.add(item("LLM_BASE_URL", "LLM 接口地址", "大模型", true, false, hasText(appProperties.getLlm().getBaseUrl())));
        items.add(item("LLM_API_KEY", "LLM API Key", "大模型", true, true, hasText(appProperties.getLlm().getApiKey())));
        items.add(item("LLM_MODEL", "LLM 模型名称", "大模型", true, false, hasText(appProperties.getLlm().getModel())));
        items.add(item("EMBEDDING_BASE_URL", "Embedding 接口地址", "Embedding", true, false, hasText(appProperties.getEmbedding().getBaseUrl())));
        items.add(item("EMBEDDING_API_KEY", "Embedding API Key", "Embedding", true, true, hasText(appProperties.getEmbedding().getApiKey())));
        items.add(item("EMBEDDING_MODEL", "Embedding 模型名称", "Embedding", true, false, hasText(appProperties.getEmbedding().getModel())));
        items.add(item("CHROMA_HOST", "Chroma 地址", "向量库", true, false, hasText(appProperties.getChroma().getHost())));
        items.add(item("CHROMA_TOKEN", "Chroma Token", "向量库", false, true, hasText(appProperties.getChroma().getToken())));
        boolean webSearchEnabled = appProperties.getWebSearch().isEnabled();
        items.add(item("TAVILY_API_KEY", "Tavily API Key", "联网搜索", webSearchEnabled, true, hasText(appProperties.getWebSearch().getApiKey())));
        items.add(item("TAVILY_CURL_EXECUTABLE", "curl 命令", "联网搜索", webSearchEnabled, false, hasText(appProperties.getWebSearch().getCurlExecutable())));
        items.add(item("APP_UPLOAD_DIR", "上传目录", "文件", true, false, hasText(appProperties.getUploadDir())));

        long missingCount = items.stream()
                .filter(ConfigStatusItem::required)
                .filter(item -> !item.configured())
                .count();
        // missingCount 单独返回，前端可以直接展示风险数量，不必重复计算。
        return new AdminConfigStatusResponse(missingCount, items);
    }

    /**
     * 管理员用户列表，按创建时间倒序展示。
     */
    @Transactional(readOnly = true)
    public List<AdminUserResponse> listUsers() {
        // 管理员列表按创建时间倒序，最新注册用户更容易被看到。
        return userRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(AdminUserResponse::from)
                .toList();
    }

    /**
     * 管理端单用户洞察。
     *
     * <p>该接口保留全局 dashboard 的总览，同时让管理员能点进用户查看该用户自己的知识库、文档、问答和估算 token 用量。</p>
     */
    @Transactional(readOnly = true)
    public AdminUserDetailResponse getUserDetail(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AdminNotFoundException("用户不存在"));
        // 文档 token 来自切片记录的估算值。
        long documentTokens = longValue(documentChunkRepository.sumTokenCountByUserId(userId));
        // 聊天 token 只能按消息长度粗略估算，因为当前没有服务商账单回传。
        long chatTokens = estimateTokens(chatMessageRepository.sumContentLengthByUserId(userId));
        return AdminUserDetailResponse.of(
                user,
                knowledgeBaseRepository.countByUserId(userId),
                documentRepository.countByUserId(userId),
                documentRepository.countByUserIdAndParseStatus(userId, DocumentStatus.READY),
                documentRepository.countByUserIdAndParseStatus(userId, DocumentStatus.FAILED),
                documentChunkRepository.countByUserId(userId),
                chatMessageRepository.countByUserIdAndRole(userId, ChatMessage.ROLE_USER),
                chatMessageRepository.countByUserIdAndRole(userId, ChatMessage.ROLE_ASSISTANT),
                documentTokens,
                chatTokens,
                documentRepository.findTop5ByUserIdOrderByCreatedAtDesc(userId),
                chatMessageRepository.findTop5ByUserIdOrderByCreatedAtDesc(userId)
        );
    }

    /**
     * 启用或禁用用户，并写入管理员审计日志。
     */
    @Transactional
    public AdminUserResponse updateUserStatus(
            Long adminUserId,
            Long userId,
            UpdateUserStatusRequest request,
            String ipAddress,
            String userAgent
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AdminNotFoundException("用户不存在"));
        String beforeStatus = user.getStatus();
        user.changeStatus(request.status());
        User saved = userRepository.save(user);
        // 高权限操作必须写审计日志，后续出现误禁用时可以追溯管理员和来源。
        adminAuditLogRepository.save(AdminAuditLog.create(
                adminUserId,
                "UPDATE_USER_STATUS",
                "USER",
                userId,
                json(Map.of("beforeStatus", beforeStatus, "afterStatus", request.status())),
                ipAddress,
                userAgent
        ));
        return AdminUserResponse.from(saved);
    }

    /**
     * 最近管理员操作日志。
     */
    @Transactional(readOnly = true)
    public List<AdminAuditLogResponse> auditLogs() {
        return adminAuditLogRepository.findTop50ByOrderByCreatedAtDesc()
                .stream()
                .map(AdminAuditLogResponse::from)
                .toList();
    }

    private ConfigStatusItem item(
            String key,
            String name,
            String group,
            boolean required,
            boolean secret,
            boolean configured
    ) {
        // 这里统一生成前端文案，避免前端重复拼“已配置/缺失”逻辑。
        return new ConfigStatusItem(
                key,
                name,
                group,
                required,
                secret,
                configured,
                configured ? "已配置" : (required ? "缺失，需在 backend/.env 中补充" : "未配置，可选")
        );
    }

    private Instant todayStart() {
        ZoneId zoneId = ZoneId.systemDefault();
        // 今日上传量按本机时区计算，和用户看到的本地日期一致。
        return LocalDate.now(clock.withZone(zoneId)).atStartOfDay(zoneId).toInstant();
    }

    private Instant tomorrowStart() {
        ZoneId zoneId = ZoneId.systemDefault();
        return LocalDate.now(clock.withZone(zoneId)).plusDays(1).atStartOfDay(zoneId).toInstant();
    }

    private long estimatedTotalTokens(Long userId) {
        long documentTokens = userId == null
                ? longValue(documentChunkRepository.sumTokenCount())
                : longValue(documentChunkRepository.sumTokenCountByUserId(userId));
        Long contentLength = userId == null
                ? chatMessageRepository.sumContentLength()
                : chatMessageRepository.sumContentLengthByUserId(userId);
        // 总估算 token = 文档切片估算 + 聊天内容估算。
        return documentTokens + estimateTokens(contentLength);
    }

    private long estimateTokens(Long contentLength) {
        if (contentLength == null || contentLength <= 0) {
            return 0;
        }
        // 简化估算规则：约 4 个字符算 1 token，满足管理端趋势展示。
        return Math.max(1, (long) Math.ceil(contentLength / 4.0));
    }

    private long longValue(Long value) {
        return value == null ? 0 : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String json(Map<String, String> value) {
        try {
            // 审计详情用 JSON 保存，方便前端只读展示，也方便后续扩展字段。
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("管理员审计详情序列化失败", ex);
        }
    }
}
