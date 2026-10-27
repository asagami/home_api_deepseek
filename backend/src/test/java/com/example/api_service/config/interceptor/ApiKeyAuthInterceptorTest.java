package com.example.api_service.config.interceptor;

import com.example.api_service.engine.billing.QuotaLedger;
import com.example.api_service.engine.context.ApiContext;
import com.example.api_service.model.entity.ApiAccessLog;
import com.example.api_service.model.entity.ApiEndpointMeta;
import com.example.api_service.model.entity.ApiKeyInfo;
import com.example.api_service.model.entity.SysUser;
import com.example.api_service.model.entity.UserApiPermission;
import com.example.api_service.security.ApiKeyUtils;
import com.example.api_service.security.AuthResponseWriter;
import com.example.api_service.security.ClientIpUtils;
import com.example.api_service.security.RedisRateLimiter;
import com.example.api_service.service.ApiAccessLogService;
import com.example.api_service.service.ApiEndpointMetaService;
import com.example.api_service.service.ApiKeyInfoService;
import com.example.api_service.service.SysUserService;
import com.example.api_service.service.UserApiPermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthInterceptorTest {

    private static final String RAW_KEY = "sk-GatewayKey000000000000000000000000000000000000";
    private static final String REQUEST_PATH = "/api/v1/test/echo";
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
    private ApiAccessLogService apiAccessLogService;

    @Mock
    private RedisRateLimiter rateLimiter;

    @Mock
    private QuotaLedger quotaLedger;

    private ApiKeyAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        AuthResponseWriter responseWriter = new AuthResponseWriter(JsonMapper.builder().build());
        TaskExecutor inlineExecutor = Runnable::run;
        ClientIpUtils clientIpUtils = new ClientIpUtils();
        interceptor = new ApiKeyAuthInterceptor(apiKeyInfoService, sysUserService,
                apiEndpointMetaService, userApiPermissionService, apiAccessLogService,
                responseWriter, inlineExecutor, rateLimiter, clientIpUtils, quotaLedger);
        lenient().when(rateLimiter.allowFixedWindow(anyString(), anyInt(), any(Duration.class)))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        ApiContext.clear();
    }

    @Test
    void shouldRejectNonPostRequest() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(
                new MockHttpServletRequest("GET", REQUEST_PATH), response, new Object());

        assertFalse(allowed);
        assertEquals(405, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":40500"));
    }

    @Test
    void shouldPopulateBillingContextForValidRequest() throws Exception {
        stubAuthorizedCall(1000L, 10, "0.50");
        MockHttpServletRequest request = postRequest();

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertTrue(allowed);
        ApiContext context = ApiContext.get();
        assertEquals(USER_ID, context.getUserId());
        assertEquals(KEY_ID, context.getApiKeyId());
        assertEquals(ENDPOINT_ID, context.getEndpointId());
        assertEquals(10, context.getBaseCostPerCall());
        assertEquals(0.50d, context.getCustomDiscount());
        assertEquals(1000L, context.getUserBalanceQuota());
        assertEquals(1, context.getBillingMultiplier());
        assertNotNull(context.getBillingCallback(), "拦截器应注入计费回调");
    }

    @Test
    void shouldChargeViaCallbackAndWriteAccessLogWhenBillable() throws Exception {
        stubAuthorizedCall(1000L, 10, "0.50");
        when(quotaLedger.preDeduct(USER_ID, 10)).thenReturn(QuotaLedger.ChargeResult.redis(true));
        MockHttpServletRequest request = postRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));

        // 模拟 AbstractApiHandler.execute() 的预扣费阶段
        ApiContext context = ApiContext.get();
        context.setBillingMultiplier(2);
        assertTrue(context.charge(10), "余额充足时预扣费应成功");
        assertEquals(10, context.getChargedCost());
        assertEquals(QuotaLedger.LedgerMode.REDIS, context.getLedgerMode());

        ApiContext.markBillable(true);
        response.setStatus(200);

        interceptor.afterCompletion(request, response, new Object(), null);

        verify(quotaLedger).preDeduct(USER_ID, 10);
        // 业务成功后异步将扣费结果增量刷回 MySQL
        verify(quotaLedger).settle(USER_ID, 10, QuotaLedger.LedgerMode.REDIS);
        ArgumentCaptor<ApiAccessLog> captor = ArgumentCaptor.forClass(ApiAccessLog.class);
        verify(apiAccessLogService).save(captor.capture());
        ApiAccessLog accessLog = captor.getValue();
        assertEquals(USER_ID, accessLog.getUserId());
        assertEquals(KEY_ID, accessLog.getApiKeyId());
        assertEquals(REQUEST_PATH, accessLog.getEndpointPath());
        assertEquals(200, accessLog.getHttpStatus());
        assertEquals(10, accessLog.getCostPoints());
        assertNull(ApiContext.get());
    }

    @Test
    void shouldNotChargeOrLogWhenNotBillable() throws Exception {
        stubAuthorizedCall(1000L, 10, "1.00");
        MockHttpServletRequest request = postRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));
        // 既未预扣费也未标记 billable（模拟请求未进入 execute 成功路径）
        response.setStatus(500);

        interceptor.afterCompletion(request, response, new Object(), null);

        verify(quotaLedger, never()).preDeduct(any(), anyInt());
        verify(apiAccessLogService, never()).save(any());
        assertNull(ApiContext.get());
    }

    @Test
    void shouldRejectChargeWhenInsufficientQuota() throws Exception {
        stubAuthorizedCall(0L, 10, "1.00");
        when(quotaLedger.preDeduct(USER_ID, 10)).thenReturn(QuotaLedger.ChargeResult.redis(false));
        MockHttpServletRequest request = postRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));

        // 预扣费失败：execute() 会抛 QUOTA_INSUFFICIENT，billable 保持 false
        ApiContext context = ApiContext.get();
        assertFalse(context.charge(10), "余额不足时预扣费应返回 false");
        assertEquals(0, context.getChargedCost(), "扣费失败不应记录已扣金额");

        // billable 为 false，afterCompletion 不写日志
        interceptor.afterCompletion(request, response, new Object(), null);

        verify(quotaLedger).preDeduct(USER_ID, 10);
        verify(apiAccessLogService, never()).save(any());
        assertNull(ApiContext.get());
    }

    @Test
    void shouldRefundChargedCostWhenBusinessFails() throws Exception {
        stubAuthorizedCall(1000L, 10, "1.00");
        when(quotaLedger.preDeduct(USER_ID, 10)).thenReturn(QuotaLedger.ChargeResult.redis(true));
        MockHttpServletRequest request = postRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));

        // 预扣费成功
        ApiContext context = ApiContext.get();
        assertTrue(context.charge(10));
        assertEquals(10, context.getChargedCost());

        // 业务失败 → execute() 的 catch 块触发补偿返还，billable 保持 false
        context.refund();
        verify(quotaLedger).compensate(USER_ID, 10, QuotaLedger.LedgerMode.REDIS);

        interceptor.afterCompletion(request, response, new Object(), null);

        // billable 为 false，不写调用日志、不 settle
        verify(quotaLedger, never()).settle(any(), anyInt(), any());
        verify(apiAccessLogService, never()).save(any());
        assertNull(ApiContext.get());
    }

    private void stubAuthorizedCall(long balanceQuota, int costPerCall, String discount) {
        when(apiKeyInfoService.getByKeyHash(ApiKeyUtils.sha256(RAW_KEY))).thenReturn(keyInfo());
        when(sysUserService.getById(USER_ID)).thenReturn(user(balanceQuota));
        when(apiEndpointMetaService.getByPathAndMethod(REQUEST_PATH, "POST"))
                .thenReturn(endpoint(costPerCall));
        when(userApiPermissionService.getApprovedPermission(USER_ID, ENDPOINT_ID))
                .thenReturn(permission(discount));
    }

    private MockHttpServletRequest postRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", REQUEST_PATH);
        request.addHeader("X-API-Key", RAW_KEY);
        return request;
    }

    private ApiKeyInfo keyInfo() {
        ApiKeyInfo keyInfo = new ApiKeyInfo();
        keyInfo.setId(KEY_ID);
        keyInfo.setUserId(USER_ID);
        keyInfo.setKeyPrefix("sk-GatewayKey...0000");
        keyInfo.setRateLimitQps(1000);
        keyInfo.setStatus(1);
        return keyInfo;
    }

    private SysUser user(Long balanceQuota) {
        SysUser user = new SysUser();
        user.setId(USER_ID);
        user.setUsername("gateway_user");
        user.setRole("USER");
        user.setBalanceQuota(balanceQuota);
        user.setStatus(1);
        return user;
    }

    private ApiEndpointMeta endpoint(int costPerCall) {
        ApiEndpointMeta endpoint = new ApiEndpointMeta();
        endpoint.setId(ENDPOINT_ID);
        endpoint.setPath(REQUEST_PATH);
        endpoint.setMethod("POST");
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
