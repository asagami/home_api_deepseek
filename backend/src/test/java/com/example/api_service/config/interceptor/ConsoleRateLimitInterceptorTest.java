package com.example.api_service.config.interceptor;

import com.example.api_service.security.AuthResponseWriter;
import com.example.api_service.security.ClientIpUtils;
import com.example.api_service.security.RedisRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("控制台限流拦截器单元测试")
class ConsoleRateLimitInterceptorTest {

    private static final int AUTH_LIMIT = 20;
    private static final int GENERAL_LIMIT = 120;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    @Mock
    private RedisRateLimiter rateLimiter;

    private ConsoleRateLimitInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new ConsoleRateLimitInterceptor(
                rateLimiter,
                new AuthResponseWriter(JsonMapper.builder().build()),
                new ClientIpUtils(),
                AUTH_LIMIT,
                GENERAL_LIMIT);
    }

    @Test
    @DisplayName("OPTIONS 预检请求应直接放行且不触碰限流器")
    void shouldBypassPreflightOptionsRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/console/auth/login");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        verifyNoInteractions(rateLimiter);
    }

    @ParameterizedTest(name = "{0} 应使用匿名认证桶")
    @ValueSource(strings = {
            "/api/console/auth/login",
            "/api/console/auth/register",
            "/api/console/auth/send-email-code",
            "/api/console/auth/forgot-password",
            "/api/console/auth/reset-password",
            "/api/console/auth/reset-password/verify"})
    @DisplayName("匿名认证路径应使用更严格的每 IP 认证桶")
    void shouldUseStricterAuthBucketForAnonymousAuthPaths(String path) throws Exception {
        when(rateLimiter.allowFixedWindow(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr("203.0.113.9");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));

        ArgumentCaptor<String> bucket = ArgumentCaptor.forClass(String.class);
        verify(rateLimiter).allowFixedWindow(bucket.capture(), eq(AUTH_LIMIT), eq(WINDOW));
        assertEquals("console:auth:ip:203.0.113.9", bucket.getValue());
    }

    @Test
    @DisplayName("普通控制台路径应使用宽松的通用桶")
    void shouldUseGeneralBucketForOtherConsolePaths() throws Exception {
        when(rateLimiter.allowFixedWindow(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/console/apikeys");
        request.setRemoteAddr("198.51.100.7");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));

        verify(rateLimiter).allowFixedWindow(eq("console:ip:198.51.100.7"), eq(GENERAL_LIMIT), eq(WINDOW));
    }

    @Test
    @DisplayName("超限时应返回 429 统一错误响应并阻断请求")
    void shouldRejectWithTooManyRequestsWhenLimitExceeded() throws Exception {
        when(rateLimiter.allowFixedWindow(anyString(), anyInt(), any(Duration.class))).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/console/auth/login");
        request.setRemoteAddr("203.0.113.9");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));

        assertEquals(429, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":42900"));
        assertTrue(response.getContentAsString().contains("请求过于频繁"));
    }

    @Test
    @DisplayName("默认不信任转发头，IP 应取自 remoteAddr")
    void shouldIgnoreForwardedHeadersWhenNotTrusted() throws Exception {
        when(rateLimiter.allowFixedWindow(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/console/apikeys");
        request.setRemoteAddr("198.51.100.7");
        request.addHeader("X-Forwarded-For", "6.6.6.6");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));

        verify(rateLimiter).allowFixedWindow(eq("console:ip:198.51.100.7"), eq(GENERAL_LIMIT), eq(WINDOW));
    }
}
