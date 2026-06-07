package com.evirag.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.evirag.auth.EmailVerificationCodeRepository;
import com.evirag.auth.JwtService;
import com.evirag.auth.VerificationEmailSender;
import com.evirag.chat.ChatMessage;
import com.evirag.chat.ChatMessageRepository;
import com.evirag.chat.ChatSessionRepository;
import com.evirag.document.DocumentChunkRepository;
import com.evirag.document.DocumentRepository;
import com.evirag.document.DocumentStatus;
import com.evirag.knowledge.KnowledgeBaseRepository;
import com.evirag.user.User;
import com.evirag.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 管理员接口与权限集成测试。
 *
 * <p>该测试启动真实 MVC 和安全过滤器链，用 mock 仓储隔离数据库，重点验证 ADMIN 权限、统计字段和配置脱敏边界。</p>
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
        "evirag.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "evirag.llm.api-key=test-llm-secret",
        "evirag.embedding.api-key=test-embedding-secret",
        "spring.mail.password=test-mail-secret"
})
@AutoConfigureMockMvc
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private EmailVerificationCodeRepository emailVerificationCodeRepository;

    @MockBean
    private VerificationEmailSender verificationEmailSender;

    @MockBean
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @MockBean
    private DocumentRepository documentRepository;

    @MockBean
    private DocumentChunkRepository documentChunkRepository;

    @MockBean
    private ChatSessionRepository chatSessionRepository;

    @MockBean
    private ChatMessageRepository chatMessageRepository;

    @MockBean
    private AdminAuditLogRepository adminAuditLogRepository;

    @Test
    void normalUserCannotAccessAdminDashboard() throws Exception {
        User user = activeUser(1L, "user@example.com", "USER");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/admin/dashboard").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void adminCanReadDashboardMetrics() throws Exception {
        User admin = activeUser(2L, "admin@example.com", "ADMIN");
        when(userRepository.findById(2L)).thenReturn(Optional.of(admin));
        when(userRepository.count()).thenReturn(8L);
        when(userRepository.countByStatus("ACTIVE")).thenReturn(6L);
        when(userRepository.countByStatus("DISABLED")).thenReturn(2L);
        when(knowledgeBaseRepository.count()).thenReturn(5L);
        when(documentRepository.count()).thenReturn(12L);
        when(documentRepository.countByParseStatus(DocumentStatus.READY)).thenReturn(9L);
        when(documentRepository.countByParseStatus(DocumentStatus.FAILED)).thenReturn(1L);
        when(chatMessageRepository.countByRole(ChatMessage.ROLE_USER)).thenReturn(33L);
        when(documentRepository.countByCreatedAtBetween(any(Instant.class), any(Instant.class))).thenReturn(4L);

        mockMvc.perform(get("/api/admin/dashboard").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalUsers").value(8))
                .andExpect(jsonPath("$.data.activeUsers").value(6))
                .andExpect(jsonPath("$.data.disabledUsers").value(2))
                .andExpect(jsonPath("$.data.totalKnowledgeBases").value(5))
                .andExpect(jsonPath("$.data.totalDocuments").value(12))
                .andExpect(jsonPath("$.data.questionCount").value(33))
                .andExpect(jsonPath("$.data.todayUploadCount").value(4));
    }

    @Test
    void configStatusOnlyReturnsConfiguredFlagsWithoutSecretValues() throws Exception {
        User admin = activeUser(2L, "admin@example.com", "ADMIN");
        when(userRepository.findById(2L)).thenReturn(Optional.of(admin));

        String body = mockMvc.perform(get("/api/admin/system/config-status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode llmApiKey = findConfigItem(body, "LLM_API_KEY");
        assertThat(llmApiKey.path("secret").asBoolean()).isTrue();
        assertThat(llmApiKey.path("configured").asBoolean()).isTrue();
        assertThat(body).doesNotContain("test-llm-secret", "test-embedding-secret", "test-mail-secret");
    }

    @Test
    void adminCanDisableUserAndWritesAuditLog() throws Exception {
        User admin = activeUser(2L, "admin@example.com", "ADMIN");
        User target = activeUser(3L, "target@example.com", "USER");
        when(userRepository.findById(2L)).thenReturn(Optional.of(admin));
        when(userRepository.findById(3L)).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(adminAuditLogRepository.save(any(AdminAuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(put("/api/admin/users/3/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(3))
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        assertThat(target.getStatus()).isEqualTo("DISABLED");
        verify(adminAuditLogRepository).save(any(AdminAuditLog.class));
    }

    private User activeUser(Long id, String email, String role) {
        User user = User.create(email, "hash");
        user.setId(id);
        user.setRole(role);
        return user;
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.createToken(user).token();
    }

    private JsonNode findConfigItem(String body, String key) throws Exception {
        for (JsonNode item : objectMapper.readTree(body).path("data").path("items")) {
            if (key.equals(item.path("key").asText())) {
                return item;
            }
        }
        throw new AssertionError("未找到配置项：" + key);
    }
}
