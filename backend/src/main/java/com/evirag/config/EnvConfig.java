package com.evirag.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 环境配置装配入口。
 *
 * <p>Task 1 已经通过 {@link DotenvBootstrap} 在 Spring 配置解析前加载 backend/.env，
 * 本类只负责把已经解析完成的 {@code evirag.*} 配置启用为强类型 {@link AppProperties}。
 * 这里刻意不再读取 .env，避免重复实现导致 JVM 参数、宿主机环境变量和本地 .env 的优先级被破坏。</p>
 */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class EnvConfig {
}
