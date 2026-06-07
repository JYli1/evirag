package com.evirag.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * AppProperties 配置绑定测试。
 *
 * <p>这里不启动完整 Spring Boot 应用，也不连接数据库；只创建最小配置上下文，
 * 专门验证 application.yml / 环境变量最终解析出的 evirag.* 配置能绑定到强类型对象。</p>
 */
class AppPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                    "evirag.max-file-size-mb=64",
                    "evirag.rag.top-k=9",
                    "evirag.rag.low-score-threshold=0.42",
                    "evirag.rag.history-turns=6"
            );

    /**
     * 验证上传大小、RAG Top-K、低相似度阈值和历史轮数都能从 Spring 配置绑定到 AppProperties。
     */
    @Test
    void bindsCoreApplicationAndRagSettings() {
        contextRunner.run(context -> {
            AppProperties properties = context.getBean(AppProperties.class);

            assertThat(properties.getMaxFileSizeMb()).isEqualTo(64);
            assertThat(properties.getRag().getTopK()).isEqualTo(9);
            assertThat(properties.getRag().getLowScoreThreshold()).isEqualTo(0.42);
            assertThat(properties.getRag().getHistoryTurns()).isEqualTo(6);
        });
    }

    /**
     * 验证上传大小必须为正数，避免上传限制被错误配置为 0 或负数。
     */
    @Test
    void rejectsNonPositiveUploadSize() {
        contextRunner.withPropertyValues("evirag.max-file-size-mb=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("maxFileSizeMb");
                });
    }

    /**
     * 验证 RAG Top-K 必须为正数，避免检索阶段拿不到任何候选文本块。
     */
    @Test
    void rejectsNonPositiveRagTopK() {
        contextRunner.withPropertyValues("evirag.rag.top-k=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("topK");
                });
    }

    /**
     * 验证低相似度阈值必须落在 0 到 1 之间，保持相似度判断语义稳定。
     */
    @Test
    void rejectsOutOfRangeLowScoreThreshold() {
        contextRunner.withPropertyValues("evirag.rag.low-score-threshold=1.5")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("lowScoreThreshold");
                });
    }

    /**
     * 验证历史轮数允许为 0，但不允许为负数。
     */
    @Test
    void rejectsNegativeHistoryTurns() {
        contextRunner.withPropertyValues("evirag.rag.history-turns=-1")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("historyTurns");
                });
    }

    /**
     * 验证模型 HTTP 超时时间必须为正数，避免下游客户端得到无效超时配置。
     */
    @Test
    void rejectsNonPositiveModelTimeout() {
        contextRunner.withPropertyValues("evirag.llm.timeout-seconds=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("timeoutSeconds");
                });
    }

    /**
     * 测试专用配置入口，只启用 AppProperties，避免把数据库、Flyway、Security 等应用组件拉进来。
     */
    @EnableConfigurationProperties(AppProperties.class)
    static class TestConfiguration {
    }
}
