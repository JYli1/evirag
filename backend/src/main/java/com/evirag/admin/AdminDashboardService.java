package com.evirag.admin;

import com.evirag.admin.dto.AdminAuditLogResponse;
import com.evirag.admin.dto.AdminConfigStatusResponse;
import com.evirag.admin.dto.AdminConfigStatusResponse.ConfigStatusItem;
import com.evirag.admin.dto.AdminDashboardResponse;
import com.evirag.admin.dto.AdminUserResponse;
import com.evirag.admin.dto.UpdateUserStatusRequest;
import com.evirag.chat.ChatMessage;
import com.evirag.chat.ChatMessageRepository;
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
                userRepository.count(),
                userRepository.countByStatus("ACTIVE"),
                userRepository.countByStatus("DISABLED"),
                knowledgeBaseRepository.count(),
                documentRepository.count(),
                documentRepository.countByParseStatus(DocumentStatus.READY),
                documentRepository.countByParseStatus(DocumentStatus.FAILED),
                chatMessageRepository.countByRole(ChatMessage.ROLE_USER),
                documentRepository.countByCreatedAtBetween(todayStart(), tomorrowStart()),
                configStatus().missingCount()
        );
    }

    /**
     * 返回配置是否缺失，不返回配置值本身。
     */
    @Transactional(readOnly = true)
    public AdminConfigStatusResponse configStatus() {
        List<ConfigStatusItem> items = new ArrayList<>();
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
        items.add(item("APP_UPLOAD_DIR", "上传目录", "文件", true, false, hasText(appProperties.getUploadDir())));

        long missingCount = items.stream()
                .filter(ConfigStatusItem::required)
                .filter(item -> !item.configured())
                .count();
        return new AdminConfigStatusResponse(missingCount, items);
    }

    /**
     * 管理员用户列表，按创建时间倒序展示。
     */
    @Transactional(readOnly = true)
    public List<AdminUserResponse> listUsers() {
        return userRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(AdminUserResponse::from)
                .toList();
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
        return LocalDate.now(clock.withZone(zoneId)).atStartOfDay(zoneId).toInstant();
    }

    private Instant tomorrowStart() {
        ZoneId zoneId = ZoneId.systemDefault();
        return LocalDate.now(clock.withZone(zoneId)).plusDays(1).atStartOfDay(zoneId).toInstant();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String json(Map<String, String> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("管理员审计详情序列化失败", ex);
        }
    }
}
