package com.evirag;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * EviRAG 后端启动上下文测试。
 * 当前 Task 1 只验证基础 Spring 容器能启动；数据库表结构和真实 MySQL 集成会在后续任务中补充。
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
})
class EviRagApplicationTest {

    /**
     * 当 SpringBoot 能完成 Bean 扫描、配置加载和上下文初始化时，此测试会自然通过。
     */
    @Test
    void contextLoads() {
    }
}
