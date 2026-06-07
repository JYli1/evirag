package com.evirag.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.evirag.config.AppProperties;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 知识库服务隔离测试。
 *
 * <p>普通用户入口必须始终携带当前登录用户 ID 查询，不能先按知识库 ID 查出数据后再在内存里判断，
 * 否则容易把其他用户的知识库详情泄露给前端。</p>
 */
class KnowledgeBaseServiceTest {

    private KnowledgeBaseRepository knowledgeBaseRepository;
    private KnowledgeBaseService knowledgeBaseService;

    @BeforeEach
    void setUp() {
        knowledgeBaseRepository = org.mockito.Mockito.mock(KnowledgeBaseRepository.class);
        AppProperties appProperties = new AppProperties();
        appProperties.getChroma().setCollectionPrefix("test_kb_");
        knowledgeBaseService = new KnowledgeBaseService(appProperties, knowledgeBaseRepository);
    }

    @Test
    void createUsesConfiguredChromaCollectionPrefix() {
        Long userId = 10L;
        when(knowledgeBaseRepository.save(any(KnowledgeBase.class))).thenAnswer(invocation -> {
            KnowledgeBase knowledgeBase = invocation.getArgument(0);
            knowledgeBase.setId(1L);
            return knowledgeBase;
        });

        KnowledgeBaseResponse response = knowledgeBaseService.create(
                userId,
                new KnowledgeBaseRequest("案件资料", "庭审材料")
        );

        assertThat(response.chromaCollection()).startsWith("test_kb_10_");
    }

    @Test
    void userCannotReadAnotherUsersKnowledgeBase() {
        Long otherUserId = 20L;
        Long knowledgeBaseId = 100L;
        when(knowledgeBaseRepository.findByIdAndUserId(knowledgeBaseId, otherUserId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> knowledgeBaseService.getById(otherUserId, knowledgeBaseId))
                .isInstanceOf(KnowledgeBaseNotFoundException.class)
                .hasMessageContaining("知识库不存在或无权访问");

        verify(knowledgeBaseRepository).findByIdAndUserId(knowledgeBaseId, otherUserId);
        verify(knowledgeBaseRepository, never()).findById(knowledgeBaseId);
    }

    @Test
    void listOnlyReturnsCurrentUsersKnowledgeBases() {
        Long userId = 10L;
        KnowledgeBase mine = KnowledgeBase.create(userId, "案件资料", "庭审材料", "rag_kb_10_a");
        when(knowledgeBaseRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(mine));

        List<KnowledgeBaseResponse> responses = knowledgeBaseService.listByCurrentUser(userId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).name()).isEqualTo("案件资料");
        verify(knowledgeBaseRepository).findByUserIdOrderByCreatedAtDesc(userId);
    }
}
