package com.evirag.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 后端异步任务配置。
 *
 * <p>文档上传接口只负责保存原始文件和创建 PROCESSING 记录，耗时较长的解析、切片、embedding 和 Chroma 写入
 * 交给独立线程池执行，避免用户上传请求被外部模型接口阻塞。</p>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 文档索引专用线程池。
     *
     * <p>课程设计 demo 不引入 Redis 或消息队列，因此这里用 Spring TaskExecutor 承载轻量异步任务；
     * 后续开源版本如果要支持大批量文档，可替换为队列消费模型。</p>
     */
    @Bean(name = "documentIndexTaskExecutor")
    public Executor documentIndexTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("evirag-index-");
        executor.initialize();
        return executor;
    }
}
