package com.evirag.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档配置。
 *
 * <p>Swagger UI 地址由 application.yml 固定为 {@code /swagger-ui/index.html}，前端或后续替换前端时都以该文档作为接口契约。</p>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI eviRagOpenApi() {
        // 这里配置的是 Swagger 页面顶部展示的信息，不会影响真实接口路径。
        return new OpenAPI()
                .info(new Info()
                        .title("EviRAG API")
                        .version("v1")
                        .description("EviRAG 证据增强文档问答系统后端接口文档"));
    }
}
