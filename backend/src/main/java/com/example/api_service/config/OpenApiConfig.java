package com.example.api_service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc OpenAPI 文档配置。
 * Swagger UI: {@code /swagger-ui.html}，OpenAPI JSON: {@code /v3/api-docs}。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI apiServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API Service")
                        .description("API 服务提供与管理平台后端接口文档")
                        .version("v1")
                        .contact(new Contact().name("API Service Team"))
                        .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}
