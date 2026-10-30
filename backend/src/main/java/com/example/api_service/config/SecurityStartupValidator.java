package com.example.api_service.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 安全配置启动校验器，在 Spring 容器初始化阶段执行 fail-fast 检查。
 *
 * <p>始终校验：</p>
 * <ul>
 *   <li>JWT 密钥已配置且长度 ≥ 32 字节（HS256 要求）。</li>
 * </ul>
 *
 * <p>仅当 {@code prod} profile 激活时额外拒绝：</p>
 * <ul>
 *   <li>使用开发默认 JWT 密钥（可被离线伪造任意令牌）。</li>
 *   <li>沙箱模拟支付开启（任何登录用户可无限白嫖点数）。</li>
 *   <li>Swagger / OpenAPI 文档公网暴露（/swagger-ui.html 与 /v3/api-docs
 *       会把全部控制台接口结构与请求示例交给攻击者）。</li>
 *   <li>信任转发头但未启用容器转发头处理（{@code app.security.trust-forwarded-headers=true}
 *       而 {@code server.forward-headers-strategy=NONE}）：此时转发头未经验证，
 *       攻击者可伪造 X-Forwarded-For 污染 client_ip。</li>
 *   <li>发码接口人机校验关闭（{@code app.captcha.turnstile.enabled=false}）：
 *       匿名脚本可直接轰炸邮件发送并批量注册。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityStartupValidator {

    /** 打包在代码中的开发默认密钥，生产环境禁止使用。 */
    public static final String DEV_JWT_SECRET = "api-service-dev-jwt-secret-change-me-in-production-2026";
    private static final int MIN_JWT_SECRET_BYTES = 32;

    private final Environment environment;

    @Value("${app.jwt.secret:}")
    private String jwtSecret;

    @Value("${app.recharge.mock-pay-enabled:false}")
    private boolean mockPayEnabled;

    @Value("${app.security.trust-forwarded-headers:false}")
    private boolean trustForwardedHeaders;

    @Value("${app.captcha.turnstile.enabled:false}")
    private boolean turnstileEnabled;

    @Value("${app.captcha.turnstile.secret-key:}")
    private String turnstileSecretKey;

    @Value("${app.password-reset.link-base-url:}")
    private String passwordResetLinkBaseUrl;

    @PostConstruct
    public void validate() {
        boolean isProd = Arrays.asList(environment.getActiveProfiles()).contains("prod");

        // ---- 始终校验：JWT 密钥长度 ----
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                    "app.jwt.secret 未配置，请通过环境变量 JWT_SECRET 设置至少 "
                            + MIN_JWT_SECRET_BYTES + " 字节的密钥");
        }
        int secretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8).length;
        if (secretBytes < MIN_JWT_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret 长度不足，HS256 要求至少 " + MIN_JWT_SECRET_BYTES
                            + " 字节，当前仅 " + secretBytes + " 字节");
        }

        // ---- 始终校验：信任转发头必须配套容器转发头处理 ----
        assertForwardedHeadersStrategy();

        // ---- 始终校验：启用 Turnstile 就必须有密钥，否则每次校验都必然失败 ----
        if (turnstileEnabled && (turnstileSecretKey == null || turnstileSecretKey.isBlank())) {
            throw new IllegalStateException(
                    "app.captcha.turnstile.enabled=true 但未配置 secret-key，"
                            + "请通过环境变量 CAPTCHA_TURNSTILE_SECRET_KEY 设置 Turnstile 服务端密钥");
        }

        // ---- 仅生产环境：拒绝危险默认值 ----
        if (isProd) {
            if (DEV_JWT_SECRET.equals(jwtSecret)) {
                throw new IllegalStateException(
                        "生产环境禁止使用开发默认 JWT 密钥，请通过环境变量 JWT_SECRET 设置独立的强密钥");
            }
            if (mockPayEnabled) {
                throw new IllegalStateException(
                        "生产环境禁止开启沙箱模拟支付（app.recharge.mock-pay-enabled=true），"
                                + "任意登录用户可自行完成支付并无限刷取点数");
            }
            assertSwaggerDisabledInProd();
            assertCaptchaEnabledInProd();
            assertPasswordResetLinkConfiguredInProd();
            log.info("安全启动校验通过：生产环境 JWT 密钥、支付、Swagger 与人机校验配置均合规");
        } else {
            if (DEV_JWT_SECRET.equals(jwtSecret)) {
                log.warn("安全告警：当前使用开发默认 JWT 密钥，生产环境部署前必须通过 JWT_SECRET 环境变量覆盖");
            }
            if (mockPayEnabled) {
                log.warn("安全告警：沙箱模拟支付已开启，任何登录用户可自行完成支付加点，请勿在生产环境使用");
            }
        }
    }

    /**
     * 生产环境必须开启发码接口的人机校验。
     *
     * <p>关闭等于把匿名发送接口直接暴露给脚本：邮件轰炸、6 位验证码定向爆破、
     * 批量注册都会失去第一道闸门，因此缺省关闭即 fail-fast，
     * 需要显式设置 {@code CAPTCHA_TURNSTILE_ENABLED=false} 才能跳过（不建议）。</p>
     */
    private void assertCaptchaEnabledInProd() {
        if (!turnstileEnabled) {
            throw new IllegalStateException(
                    "生产环境必须开启发码接口人机校验（app.captcha.turnstile.enabled=true），"
                            + "否则匿名脚本可直接轰炸邮件发送并注册批量账号；"
                            + "请配置 Turnstile 密钥或显式评估风险后强制关闭");
        }
    }

    /**
     * 信任转发头（{@code app.security.trust-forwarded-headers=true}）必须配套开启
     * 容器转发头处理（{@code server.forward-headers-strategy=NATIVE} 或 {@code FRAMEWORK}）。
     *
     * <p>仅信任应用层解析转发头而不让 Tomcat 规范化转发链，等于让未经校验的
     * {@code X-Forwarded-For} 直接进入落库逻辑——攻击者可任意伪造 client_ip。
     * 同时拒绝显式 {@code NONE}：那是“明确不处理”的语义，与信任开关自相矛盾。</p>
     */
    private void assertForwardedHeadersStrategy() {
        if (!trustForwardedHeaders) {
            return;
        }
        String strategy = environment.getProperty("server.forward-headers-strategy", "NONE");
        if ("NONE".equalsIgnoreCase(strategy) || strategy == null || strategy.isBlank()) {
            throw new IllegalStateException(
                    "app.security.trust-forwarded-headers=true 时必须同时设置 "
                            + "server.forward-headers-strategy=NATIVE（或 FRAMEWORK），"
                            + "当前为 '" + strategy + "'；否则调用方可伪造 X-Forwarded-For 污染 client_ip");
        }
    }

    /**
     * 生产环境必须关闭 Swagger / OpenAPI 文档公网暴露。
     *
     * <p>检查 SpringDoc 3.x 的三个开关属性：{@code springdoc.api-docs.enabled}、
     * {@code springdoc.swagger-ui.enabled}、{@code springdoc.swagger-ui.try-it-out-enabled}。
     * 任意一个未显式置 false 即 fail-fast，避免 application-prod.properties 漏配或被覆盖。
     * 缺省值取 "true" 与 SpringDoc 默认行为一致。</p>
     */
    private void assertSwaggerDisabledInProd() {
        String apiDocsEnabled = environment.getProperty("springdoc.api-docs.enabled", "true");
        String swaggerUiEnabled = environment.getProperty("springdoc.swagger-ui.enabled", "true");
        String tryItOutEnabled = environment.getProperty("springdoc.swagger-ui.try-it-out-enabled", "true");

        if (Boolean.parseBoolean(apiDocsEnabled)) {
            throw new IllegalStateException(
                    "生产环境禁止开启 OpenAPI 文档端点（springdoc.api-docs.enabled="
                            + apiDocsEnabled + "），/v3/api-docs 会泄露全部控制台接口结构；"
                            + "请激活 prod profile 或显式设置该属性为 false");
        }
        if (Boolean.parseBoolean(swaggerUiEnabled)) {
            throw new IllegalStateException(
                    "生产环境禁止开启 Swagger UI（springdoc.swagger-ui.enabled="
                            + swaggerUiEnabled + "），/swagger-ui.html 会把控制台接口结构"
                            + "与请求示例交给攻击者；请激活 prod profile 或显式设置该属性为 false");
        }
        if (Boolean.parseBoolean(tryItOutEnabled)) {
            throw new IllegalStateException(
                    "生产环境禁止开启 Swagger try-it-out（springdoc.swagger-ui.try-it-out-enabled="
                            + tryItOutEnabled + "），即便 UI 已下线也必须保持 false 以防配置回滚时单点泄露");
        }
    }

    /**
     * 生产环境的密码重置链接必须指向可公网访问的前端地址。
     *
     * <p>默认值是 {@code http://localhost:5173/reset-password}，直接上线会让所有用户
     * 收到打不开的重置邮件；缺省或仍指向本机时 fail-fast。</p>
     */
    private void assertPasswordResetLinkConfiguredInProd() {
        String url = passwordResetLinkBaseUrl;
        if (url == null || url.isBlank()
                || url.contains("localhost") || url.contains("127.0.0.1")) {
            throw new IllegalStateException(
                    "生产环境必须通过 PASSWORD_RESET_LINK_BASE_URL 配置公网可访问的重置密码页面地址，"
                            + "当前为 '" + url + "'；否则找回密码邮件中的链接不可用");
        }
    }
}
