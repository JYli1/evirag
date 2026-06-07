package com.evirag.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * DotenvBootstrap 单元测试。
 * 通过临时 .env 文件验证配置会进入 JVM 系统属性，从而能被 Spring 的 ${KEY:default} 占位符读取。
 */
class DotenvBootstrapTest {

    @TempDir
    Path tempDir;

    /**
     * 验证普通值、带引号的值和包含等号的值都能从 .env 加载为系统属性。
     */
    @Test
    void loadsDotenvValuesIntoSystemProperties() throws IOException {
        Path dotenv = tempDir.resolve(".env");
        Files.writeString(dotenv, String.join(System.lineSeparator(),
                "# 注释行不应被解析为配置",
                "APP_PORT=19090",
                "MAIL_FROM=\"noreply@example.com\"",
                "DB_PASSWORD='abc=123'"
        ), StandardCharsets.UTF_8);

        clearProperties("APP_PORT", "MAIL_FROM", "DB_PASSWORD");
        try {
            DotenvBootstrap.load(dotenv);

            assertEquals("19090", System.getProperty("APP_PORT"));
            assertEquals("noreply@example.com", System.getProperty("MAIL_FROM"));
            assertEquals("abc=123", System.getProperty("DB_PASSWORD"));
        } finally {
            clearProperties("APP_PORT", "MAIL_FROM", "DB_PASSWORD");
        }
    }

    /**
     * 验证 JVM -D 参数优先级高于 .env，避免启动脚本显式传入的值被文件覆盖。
     */
    @Test
    void keepsExistingJvmSystemProperty() throws IOException {
        Path dotenv = tempDir.resolve(".env");
        Files.writeString(dotenv, "APP_PORT=19090", StandardCharsets.UTF_8);

        System.setProperty("APP_PORT", "18080");
        try {
            DotenvBootstrap.load(dotenv);

            assertEquals("18080", System.getProperty("APP_PORT"));
        } finally {
            clearProperties("APP_PORT");
        }
    }

    private static void clearProperties(String... keys) {
        for (String key : keys) {
            System.clearProperty(key);
        }
    }
}
