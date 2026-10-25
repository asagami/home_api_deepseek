package com.example.api_service.security;

import com.example.api_service.model.entity.ApiEndpointMeta;
import com.example.api_service.model.entity.ApiKeyInfo;
import com.example.api_service.model.entity.SysUser;
import com.example.api_service.model.entity.UserApiPermission;
import com.example.api_service.service.ApiEndpointMetaService;
import com.example.api_service.service.ApiKeyInfoService;
import com.example.api_service.service.SysUserService;
import com.example.api_service.service.UserApiPermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthInterceptorTest {

    private static final String RAW_KEY = "sk-TestKeyForInterceptor0000000000000000000000000000";
    private static final String REQUEST_PATH = "/api/v1/services/echo";
    private static final Long USER_ID = 1L;
    private static final Long KEY_ID = 100L;
    private static final Long ENDPOINT_ID = 1001L;

    @Mock
    private ApiKeyInfoService apiKeyInfoService;

    @Mock
    private SysUserService sysUserService;

    @Mock
    private ApiEndpointMetaService apiEndpointMetaService;

    @Mock
    private UserApiPermissionService userApiPermissionService;

    @Mock
    private RedisRateLimiter rateLimiter;

    private ApiKeyAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        AuthResponseWriter responseWriter = new AuthResponseWriter(JsonMapper.builder().build());
        interceptor = new ApiKeyAuthInterceptor(apiKeyInfoService, sysUserService,
                apiEndpointMetaService, userApiPermissionService, responseWriter, rateLimiter);
        lenient().when(rateLimiter.allowFixedWindow(anyString(), anyInt(), any(Duration.class)))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        ApiKeyContext.clear();
    }

    @Test
    void shouldRejectMissingApiKey() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request(null), response, new Object());

        assertFalse(allowed);
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":40101"));
    }

    @Test
    void shouldRejectUnknownApiKey() throws Exception {
        when(apiKeyInfoService.getByKeyHash(ApiKeyUtils.sha256(RAW_KEY))).thenReturn(null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request(RAW_KEY), response, new Object());

        assertFalse(allowed);
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":40102"));
    }

    @Test
    void shouldRejectDisabledApiKey() throws Exception {
        ApiKeyInfo keyInfo = keyInfo();
        keyInfo.setStatus(0);
        when(apiKeyInfoService.getByKeyHash(ApiKeyUtils.sha256(RAW_KEY))).thenReturn(keyInfo);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request(RAW_KEY), response, new Object());

        assertFalse(allowed);
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":40103"));
    }

    @Test
    void shouldRejectExpiredApiKey() throws Exception {
        ApiKeyInfo keyInfo = keyInfo();
        keyInfo.setExpiredTime(LocalDateTime.now().minusMinutes(1));
        when(apiKeyInfoService.getByKeyHash(ApiKeyUtils.sha256(RAW_KEY))).thenReturn(keyInfo);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request(RAW_KEY), response, new Object());

        assertFalse(allowed);
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":40104"));
    }

    @Test
    void shouldRejectDisabledAccount() throws Exception {
        SysUser user = user(1000L);
        user.setStatus(0);
        when(apiKeyInfoService.getByKeyHash(ApiKeyUtils.sha256(RAW_KEY))).thenReturn(keyInfo());
        when(sysUserService.getById(USER_ID)).thenReturn(user);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request(RAW_KEY), response, new Object());

        assertFalse(allowed);
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":40301"));
    }

    @Test
    void shouldRejectEndpointNotInCatalog() throws Exception {
        stubKeyAndUser(1000L);
        when(apiEndpointMetaService.getByPathAndMethod(REQUEST_PATH, "GET")).thenReturn(null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request(RAW_KEY), response, new Object());

        assertFalse(allowed);
        assertEquals(404, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":40404"));
    }

    @Test
    void shouldRejectWithoutApprovedPermission() throws Exception {
        stubKeyAndUser(1000L);
        when(apiEndpointMetaService.getByPathAndMethod(REQUEST_PATH, "GET"))
                .thenReturn(endpoint(1));
        when(userApiPermissionService.getApprovedPermission(USER_ID, ENDPOINT_ID)).thenReturn(null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request(RAW_KEY), response, new Object());

        assertFalse(allowed);
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":40304"));
    }

    @Test
    void shouldRejectWhenBalanceQuotaInsufficient() throws Exception {
        stubKeyAndUser(4L);
        when(apiEndpointMetaService.getByPathAndMethod(REQUEST_PATH, "GET"))
                .thenReturn(endpoint(5));
        when(userApiPermissionService.getApprovedPermission(USER_ID, ENDPOINT_ID))
                .thenReturn(permission("1.00"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request(RAW_KEY), response, new Object());

        assertFalse(allowed);
        assertEquals(402, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":40200"));
    }

    @Test
    void shouldRejectWhenQpsExceeded() throws Exception {
        ApiKeyInfo keyInfo = keyInfo();
        keyInfo.setRateLimitQps(1);
        when(apiKeyInfoService.getByKeyHash(ApiKeyUtils.sha256(RAW_KEY))).thenReturn(keyInfo);
        when(sysUserService.getById(USER_ID)).thenReturn(user(1000L));
        when(apiEndpointMetaService.getByPathAndMethod(REQUEST_PATH, "GET"))
                .thenReturn(endpoint(1));
        when(userApiPermissionService.getApprovedPermission(USER_ID, ENDPOINT_ID))
                .thenReturn(permission("1.00"));
        // 第一次放行，第二次拒绝（模拟 Redis 固定窗口超限）
        when(rateLimiter.allowFixedWindow(anyString(), anyInt(), any(Duration.class)))
                .thenReturn(true, false);

        assertTrue(interceptor.preHandle(request(RAW_KEY), new MockHttpServletResponse(), new Object()));
        ApiKeyContext.clear();

        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        boolean secondAllowed = interceptor.preHandle(request(RAW_KEY), secondResponse, new Object());

        assertFalse(secondAllowed);
        assertEquals(429, secondResponse.getStatus());
        assertTrue(secondResponse.getContentAsString().contains("\"code\":42900"));
    }

    @Test
    void shouldAllowValidKeyAndPopulateContextWithDiscountedCost() throws Exception {
        stubKeyAndUser(1000L);
        when(apiEndpointMetaService.getByPathAndMethod(REQUEST_PATH, "GET"))
                .thenReturn(endpoint(10));
        when(userApiPermissionService.getApprovedPermission(USER_ID, ENDPOINT_ID))
                .thenReturn(permission("0.75"));

        boolean allowed = interceptor.preHandle(request(RAW_KEY), new MockHttpServletResponse(), new Object());

        assertTrue(allowed);
        ApiKeyPrincipal principal = ApiKeyContext.get();
        assertEquals(USER_ID, principal.userId());
        assertEquals(KEY_ID, principal.apiKeyId());
        assertEquals(ENDPOINT_ID, principal.endpointId());
        // ceil(10 * 0.75) = 8
        assertEquals(8, principal.costPoints());

        interceptor.afterCompletion(request(RAW_KEY), new MockHttpServletResponse(), new Object(), null);
        assertNull(ApiKeyContext.get());
    }

    @Test
    void shouldAllowFreeEntitlementWithZeroBalance() throws Exception {
        stubKeyAndUser(0L);
        when(apiEndpointMetaService.getByPathAndMethod(REQUEST_PATH, "GET"))
                .thenReturn(endpoint(10));
        when(userApiPermissionService.getApprovedPermission(USER_ID, ENDPOINT_ID))
                .thenReturn(permission("0.00"));

        boolean allowed = interceptor.preHandle(request(RAW_KEY), new MockHttpServletResponse(), new Object());

        assertTrue(allowed);
        assertEquals(0, ApiKeyContext.get().costPoints());
    }

    private void stubKeyAndUser(long balance) {
        when(apiKeyInfoService.getByKeyHash(ApiKeyUtils.sha256(RAW_KEY))).thenReturn(keyInfo());
        when(sysUserService.getById(USER_ID)).thenReturn(user(balance));
    }

    private MockHttpServletRequest request(String apiKey) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", REQUEST_PATH);
        if (apiKey != null) {
            request.addHeader("X-API-Key", apiKey);
        }
        return request;
    }

    private ApiKeyInfo keyInfo() {
        ApiKeyInfo keyInfo = new ApiKeyInfo();
        keyInfo.setId(KEY_ID);
        keyInfo.setUserId(USER_ID);
        keyInfo.setKeyPrefix("sk-TestKey...0000");
        keyInfo.setRateLimitQps(5);
        keyInfo.setTotalQuota(-1L);
        keyInfo.setUsedQuota(0L);
        keyInfo.setStatus(1);
        return keyInfo;
    }

    private SysUser user(Long balanceQuota) {
        SysUser user = new SysUser();
        user.setId(USER_ID);
        user.setUsername("alice");
        user.setRole("USER");
        user.setBalanceQuota(balanceQuota);
        user.setStatus(1);
        return user;
    }

    private ApiEndpointMeta endpoint(int costPerCall) {
        ApiEndpointMeta endpoint = new ApiEndpointMeta();
        endpoint.setId(ENDPOINT_ID);
        endpoint.setPath(REQUEST_PATH);
        endpoint.setMethod("GET");
        endpoint.setCostPerCall(costPerCall);
        endpoint.setStatus(1);
        return endpoint;
    }

    private UserApiPermission permission(String discount) {
        UserApiPermission permission = new UserApiPermission();
        permission.setUserId(USER_ID);
        permission.setEndpointId(ENDPOINT_ID);
        permission.setStatus(1);
        permission.setCustomDiscount(new BigDecimal(discount));
        return permission;
    }
}
