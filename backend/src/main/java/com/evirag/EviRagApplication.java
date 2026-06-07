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
        DotenvBootstrap.load();
        SpringApplication.run(EviRagApplication.class, args);
    }
}
