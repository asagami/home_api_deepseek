package com.example.api_service.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static com.example.api_service.config.SecurityStartupValidator.DEV_JWT_SECRET;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("安全配置启动校验器单元测试")
class SecurityStartupValidatorTest {

    private static final String STRONG_SECRET = "unit-test-secret-at-least-32-bytes-2026";
    private static final String TURNSTILE_SECRET = "turnstile-test-secret";
    private static final String PUBLIC_RESET_URL = "https://app.example.com/reset-password";

    private SecurityStartupValidator validator(MockEnvironment env, String jwtSecret, boolean mockPay,
                                              boolean trustForwardedHeaders, boolean turnstileEnabled,
                                              String turnstileSecretKey, String passwordResetUrl) {
        SecurityStartupValidator validator = new SecurityStartupValidator(env);
        ReflectionTestUtils.setField(validator, "jwtSecret", jwtSecret);
        ReflectionTestUtils.setField(validator, "mockPayEnabled", mockPay);
        ReflectionTestUtils.setField(validator, "trustForwardedHeaders", trustForwardedHeaders);
        ReflectionTestUtils.setField(validator, "turnstileEnabled", turnstileEnabled);
        ReflectionTestUtils.setField(validator, "turnstileSecretKey", turnstileSecretKey);
        ReflectionTestUtils.setField(validator, "passwordResetLinkBaseUrl", passwordResetUrl);
        return validator;
    }

    private MockEnvironment devEnv() {
        return new MockEnvironment();
    }

    private MockEnvironment prodEnv(String apiDocs, String swaggerUi, String tryItOut) {
        MockEnvironment env = new MockEnvironment()
                .withProperty("springdoc.api-docs.enabled", apiDocs)
                .withProperty("springdoc.swagger-ui.enabled", swaggerUi)
                .withProperty("springdoc.swagger-ui.try-it-out-enabled", tryItOut);
        env.setActiveProfiles("prod");
        return env;
    }

    private MockEnvironment prodEnv() {
        return prodEnv("false", "false", "false");
    }

    // ---------- 始终校验 ----------

    @Test
    @DisplayName("JWT 密钥为空应 fail-fast")
    void shouldRejectBlankJwtSecret() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> validator(devEnv(), "  ", false, false, false, "", null).validate());
        assertTrue(ex.getMessage().contains("app.jwt.secret 未配置"));
    }

    @Test
    @DisplayName("JWT 密钥为 null 应 fail-fast")
    void shouldRejectNullJwtSecret() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> validator(devEnv(), null, false, false, false, "", null).validate());
        assertTrue(ex.getMessage().contains("app.jwt.secret 未配置"));
    }

    @Test
    @DisplayName("JWT 密钥不足 32 字节应 fail-fast")
    void shouldRejectTooShortJwtSecret() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> validator(devEnv(), "a".repeat(31), false, false, false, "", null).validate());
        assertTrue(ex.getMessage().contains("长度不足"));
    }

    @Test
    @DisplayName("非生产环境强密钥应通过")
    void shouldAcceptStrongSecretOutsideProd() {
        assertDoesNotThrow(() -> validator(devEnv(), STRONG_SECRET, false, false, false, "", null).validate());
    }

    @Test
    @DisplayName("非生产环境使用开发默认密钥只告警不阻断")
    void shouldWarnButAllowDevSecretOutsideProd() {
        assertDoesNotThrow(() -> validator(devEnv(), DEV_JWT_SECRET, false, false, false, "", null).validate());
    }

    @Test
    @DisplayName("非生产环境开启模拟支付只告警不阻断")
    void shouldWarnButAllowMockPayOutsideProd() {
        assertDoesNotThrow(() -> validator(devEnv(), STRONG_SECRET, true, false, false, "", null).validate());
    }

    @Test
    @DisplayName("信任转发头但未开启容器处理应 fail-fast")
    void shouldRejectTrustForwardedHeadersWithoutContainerStrategy() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("server.forward-headers-strategy", "NONE");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> validator(env, STRONG_SECRET, false, true, false, "", null).validate());
        assertTrue(ex.getMessage().contains("forward-headers-strategy"));
    }

    @Test
    @DisplayName("信任转发头且开启 NATIVE 处理应通过")
    void shouldAcceptTrustForwardedHeadersWithNativeStrategy() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("server.forward-headers-strategy", "NATIVE");

        assertDoesNotThrow(() -> validator(env, STRONG_SECRET, false, true, false, "", null).validate());
    }

    @Test
    @DisplayName("Turnstile 开启但未配置密钥应 fail-fast")
    void shouldRejectTurnstileEnabledWithoutSecret() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> validator(devEnv(), STRONG_SECRET, false, false, true, "", null).validate());
        assertTrue(ex.getMessage().contains("secret-key"));
    }

    // ---------- 仅生产环境 ----------

    @Test
    @DisplayName("生产环境使用开发默认密钥应 fail-fast")
    void shouldRejectDevJwtSecretInProd() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> validator(prodEnv(), DEV_JWT_SECRET, false, false, true, TURNSTILE_SECRET, PUBLIC_RESET_URL)
                        .validate());
        assertTrue(ex.getMessage().contains("开发默认 JWT 密钥"));
    }

    @Test
    @DisplayName("生产环境开启模拟支付应 fail-fast")
    void shouldRejectMockPayInProd() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> validator(prodEnv(), STRONG_SECRET, true, false, true, TURNSTILE_SECRET, PUBLIC_RESET_URL)
                        .validate());
        assertTrue(ex.getMessage().contains("模拟支付"));
    }

    @Test
    @DisplayName("生产环境开启 OpenAPI 文档端点应 fail-fast")
    void shouldRejectOpenApiDocsInProd() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> validator(prodEnv("true", "false", "false"), STRONG_SECRET, false, false,
                        true, TURNSTILE_SECRET, PUBLIC_RESET_URL).validate());
        assertTrue(ex.getMessage().contains("OpenAPI"));
    }

    @Test
    @DisplayName("生产环境开启 Swagger UI 应 fail-fast")
    void shouldRejectSwaggerUiInProd() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> validator(prodEnv("false", "true", "false"), STRONG_SECRET, false, false,
                        true, TURNSTILE_SECRET, PUBLIC_RESET_URL).validate());
        assertTrue(ex.getMessage().contains("Swagger UI"));
    }

    @Test
    @DisplayName("生产环境开启 Swagger try-it-out 应 fail-fast")
    void shouldRejectSwaggerTryItOutInProd() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> validator(prodEnv("false", "false", "true"), STRONG_SECRET, false, false,
                        true, TURNSTILE_SECRET, PUBLIC_RESET_URL).validate());
        assertTrue(ex.getMessage().contains("try-it-out"));
    }

    @Test
    @DisplayName("生产环境关闭人机校验应 fail-fast")
    void shouldRejectCaptchaDisabledInProd() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> validator(prodEnv(), STRONG_SECRET, false, false, false, "", PUBLIC_RESET_URL).validate());
        assertTrue(ex.getMessage().contains("人机校验"));
    }

    @Test
    @DisplayName("生产环境密码重置链接指向本机应 fail-fast")
    void shouldRejectLocalhostPasswordResetLinkInProd() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> validator(prodEnv(), STRONG_SECRET, false, false, true, TURNSTILE_SECRET,
                        "http://localhost:5173/reset-password").validate());
        assertTrue(ex.getMessage().contains("PASSWORD_RESET_LINK_BASE_URL"));
    }

    @Test
    @DisplayName("生产环境配置完全合规应通过")
    void shouldAcceptFullyCompliantProdConfig() {
        assertDoesNotThrow(() -> validator(prodEnv(), STRONG_SECRET, false, false,
                true, TURNSTILE_SECRET, PUBLIC_RESET_URL).validate());
    }

    @Test
    @DisplayName("生产环境信任转发头并开启 NATIVE 处理应通过")
    void shouldAcceptTrustedForwardedHeadersInProd() {
        MockEnvironment env = prodEnv();
        env.withProperty("server.forward-headers-strategy", "NATIVE");

        assertDoesNotThrow(() -> validator(env, STRONG_SECRET, false, true,
                true, TURNSTILE_SECRET, PUBLIC_RESET_URL).validate());
    }
}
