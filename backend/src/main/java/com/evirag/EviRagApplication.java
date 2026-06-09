package com.evirag;

import com.evirag.config.DotenvBootstrap;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * EviRAG 后端启动入口。
 * 负责启动认证、知识库、文档索引、RAG 问答和管理员面板等后端能力。
 */
@SpringBootApplication
public class EviRagApplication {
    public static void main(String[] args) {
        // Spring Boot 默认不会主动读取 backend/.env，所以这里必须先把本地配置加载进来。
        // 否则数据库密码、JWT 密钥、模型 Key 等占位符在启动时会读不到真实值。
        DotenvBootstrap.load();
        SpringApplication.run(EviRagApplication.class, args);
    }
}
