package com.example.api_service.config;

import com.example.api_service.config.interceptor.ConsoleRateLimitInterceptor;
import com.example.api_service.security.AdminAuthInterceptor;
import com.example.api_service.security.JwtAuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：限流、双轨鉴权拦截器与全局跨域。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtAuthInterceptor jwtAuthInterceptor;
    private final AdminAuthInterceptor adminAuthInterceptor;
    /** 旧版示例接口拦截器，仅保留给 /api/v1/services/* 过渡期使用。 */
    private final com.example.api_service.security.ApiKeyAuthInterceptor legacyApiKeyAuthInterceptor;
    /** 新版动态网关拦截器，负责计费上下文与调用记账。 */
    private final com.example.api_service.config.interceptor.ApiKeyAuthInterceptor gatewayApiKeyAuthInterceptor;
    /** 控制台轨道限流拦截器，先于鉴权执行以保护匿名接口。 */
    private final ConsoleRateLimitInterceptor consoleRateLimitInterceptor;
    private final String[] allowedOriginPatterns;

    public WebMvcConfig(
            JwtAuthInterceptor jwtAuthInterceptor,
            AdminAuthInterceptor adminAuthInterceptor,
            com.example.api_service.security.ApiKeyAuthInterceptor legacyApiKeyAuthInterceptor,
            com.example.api_service.config.interceptor.ApiKeyAuthInterceptor gatewayApiKeyAuthInterceptor,
            ConsoleRateLimitInterceptor consoleRateLimitInterceptor,
            @Value("${app.cors.allowed-origin-patterns:http://localhost:5173}")
            String[] allowedOriginPatterns) {
        this.jwtAuthInterceptor = jwtAuthInterceptor;
        this.adminAuthInterceptor = adminAuthInterceptor;
        this.legacyApiKeyAuthInterceptor = legacyApiKeyAuthInterceptor;
        this.gatewayApiKeyAuthInterceptor = gatewayApiKeyAuthInterceptor;
        this.consoleRateLimitInterceptor = consoleRateLimitInterceptor;
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 限流拦截器最先执行（order 0），保护匿名认证接口免受暴力破解与轰炸
        registry.addInterceptor(consoleRateLimitInterceptor)
                .addPathPatterns("/api/console/**")
                .order(0);

        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/api/console/**")
                .excludePathPatterns(
                        "/api/console/auth/register",
                        "/api/console/auth/login",
                        "/api/console/auth/send-email-code")
                .order(1);

        // 必须在 JWT 拦截器之后，才能读取 UserContext 中的角色
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api/console/**")
                .order(2);

        /*
         * 过渡期双轨：
         * - 旧版 /api/v1/services/* 仍由旧控制器和旧拦截器承载（含 GET 示例接口）；
         * - 其余 /api/v1/** 全部走新的 POST + JSON 动态网关。
         * 旧控制器下线后，删除 legacy 注册与 excludePathPatterns 即可完成收口。
         */
        String[] legacyOpenApiPaths = {
                "/api/v1/services/echo",
                "/api/v1/services/text-stats"
        };
        registry.addInterceptor(legacyApiKeyAuthInterceptor)
                .addPathPatterns(legacyOpenApiPaths)
                .order(3);

        registry.addInterceptor(gatewayApiKeyAuthInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(legacyOpenApiPaths)
                .order(3);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Content-Disposition")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
