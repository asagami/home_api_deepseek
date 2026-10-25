package com.example.api_service.integration;

import com.example.api_service.model.entity.ApiAccessLog;
import com.example.api_service.model.entity.SysUser;
import com.example.api_service.model.entity.UserApiPermission;
import com.example.api_service.security.ApiKeyUtils;
import com.example.api_service.service.ApiAccessLogService;
import com.example.api_service.service.ApiEndpointMetaService;
import com.example.api_service.service.ApiKeyInfoService;
import com.example.api_service.service.EmailService;
import com.example.api_service.service.SysUserService;
import com.example.api_service.service.UserApiPermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PermissionApiIntegrationTest {

    private static final String AUTH_HEADER = "Authorization";
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String PASSWORD = "password123";
    private static final long ECHO_ENDPOINT_ID = 1001L;
    private static final long TEXT_STATS_ENDPOINT_ID = 1002L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private ApiKeyInfoService apiKeyInfoService;

    @Autowired
    private ApiAccessLogService apiAccessLogService;

    @Autowired
    private UserApiPermissionService userApiPermissionService;

    @Autowired
    private ApiEndpointMetaService apiEndpointMetaService;

    /** 集成测试不依赖真实 Redis / SMTP，验证码校验由该 Mock 通过 */
    @MockitoBean
    private EmailService emailService;

    @Test
    void shouldExposeMarketAndSupportApplyApproveRejectAndRevoke() throws Exception {
        String userToken = registerAndLogin("perm_user_it");

        mockMvc.perform(get("/api/console/permissions/market").header(AUTH_HEADER, bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(String.valueOf(ECHO_ENDPOINT_ID)))
                .andExpect(jsonPath("$.data[0].path").value("/api/v1/services/echo"))
                .andExpect(jsonPath("$.data[0].baseCost").value(1))
                .andExpect(jsonPath("$.data[0].discountedCost").value(1))
                .andExpect(jsonPath("$.data[0].applyStatus").isEmpty());

        mockMvc.perform(post("/api/console/permissions/apply")
                        .header(AUTH_HEADER, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "endpointId", ECHO_ENDPOINT_ID,
                                "applyReason", "生产环境需要调用回声接口"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(0));

        long echoPermissionId = findPermission(findUser("perm_user_it").getId(), ECHO_ENDPOINT_ID).getId();

        mockMvc.perform(post("/api/console/permissions/apply")
                        .header(AUTH_HEADER, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "endpointId", ECHO_ENDPOINT_ID,
                                "applyReason", "重复提交"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40902));

        mockMvc.perform(post("/api/console/permissions/apply")
                        .header(AUTH_HEADER, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "endpointId", 999_999L,
                                "applyReason", "不存在的接口"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40404));

        String adminToken = createAdminToken("perm_admin_it");

        mockMvc.perform(get("/api/console/admin/permissions/list")
                        .header(AUTH_HEADER, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].username").value("perm_user_it"))
                .andExpect(jsonPath("$.data.records[0].endpointName").value("回声测试"))
                .andExpect(jsonPath("$.data.records[0].baseCost").value(1))
                .andExpect(jsonPath("$.data.records[0].status").value(0));

        mockMvc.perform(get("/api/console/admin/permissions/list")
                        .header(AUTH_HEADER, bearer(userToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));

        mockMvc.perform(put("/api/console/admin/permissions/{id}/audit", echoPermissionId)
                        .header(AUTH_HEADER, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "action", "APPROVE",
                                "customDiscount", new BigDecimal("0.80"),
                                "auditRemark", "同意开通"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.customDiscount").value(0.8));

        mockMvc.perform(post("/api/console/permissions/apply")
                        .header(AUTH_HEADER, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "endpointId", ECHO_ENDPOINT_ID,
                                "applyReason", "已通过后重复申请"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40903));

        // 管理员调整基础点数后，市场中的折后点数应按专属折扣重新计算
        mockMvc.perform(put("/api/console/admin/permissions/endpoints/{id}/cost", ECHO_ENDPOINT_ID)
                        .header(AUTH_HEADER, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("costPerCall", 10))))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/console/admin/permissions/endpoints/{id}/cost", ECHO_ENDPOINT_ID)
                        .header(AUTH_HEADER, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("costPerCall", -1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));

        mockMvc.perform(get("/api/console/permissions/market").header(AUTH_HEADER, bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].baseCost").value(10))
                .andExpect(jsonPath("$.data[0].discountedCost").value(8))
                .andExpect(jsonPath("$.data[0].discountRate").value(0.8))
                .andExpect(jsonPath("$.data[0].applyStatus").value(1));

        // 驳回流程：必须填写审批意见
        mockMvc.perform(post("/api/console/permissions/apply")
                        .header(AUTH_HEADER, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "endpointId", TEXT_STATS_ENDPOINT_ID,
                                "applyReason", "需要文本统计能力"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(0));

        long textPermissionId = findPermission(
                findUser("perm_user_it").getId(), TEXT_STATS_ENDPOINT_ID).getId();

        mockMvc.perform(put("/api/console/admin/permissions/{id}/audit", textPermissionId)
                        .header(AUTH_HEADER, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("action", "REJECT"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));

        mockMvc.perform(put("/api/console/admin/permissions/{id}/audit", textPermissionId)
                        .header(AUTH_HEADER, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "action", "APPROVE",
                                "customDiscount", new BigDecimal("1.50")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));

        mockMvc.perform(put("/api/console/admin/permissions/{id}/audit", textPermissionId)
                        .header(AUTH_HEADER, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "action", "REJECT",
                                "auditRemark", "业务场景与接口定位不匹配"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(2))
                .andExpect(jsonPath("$.data.auditRemark").value("业务场景与接口定位不匹配"));

        mockMvc.perform(get("/api/console/permissions/market").header(AUTH_HEADER, bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[1].applyStatus").value(2))
                .andExpect(jsonPath("$.data[1].auditRemark").value("业务场景与接口定位不匹配"));

        // 已驳回的申请可在原记录上重新提交
        mockMvc.perform(post("/api/console/permissions/apply")
                        .header(AUTH_HEADER, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "endpointId", TEXT_STATS_ENDPOINT_ID,
                                "applyReason", "补充说明后重新提交"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(String.valueOf(textPermissionId)))
                .andExpect(jsonPath("$.data.status").value(0));

        mockMvc.perform(get("/api/console/admin/permissions/list")
                        .header(AUTH_HEADER, bearer(adminToken))
                        .param("status", "0")
                        .param("keyword", "perm_user_it"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].endpointId").value(String.valueOf(TEXT_STATS_ENDPOINT_ID)));

        // 收回已通过的权限后，折扣不再生效
        mockMvc.perform(put("/api/console/admin/permissions/{id}/audit", echoPermissionId)
                        .header(AUTH_HEADER, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "action", "REVOKE",
                                "auditRemark", "违规调用"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(3));

        mockMvc.perform(put("/api/console/admin/permissions/{id}/audit", echoPermissionId)
                        .header(AUTH_HEADER, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("action", "REVOKE"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900));

        mockMvc.perform(get("/api/console/permissions/market").header(AUTH_HEADER, bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].applyStatus").value(3))
                .andExpect(jsonPath("$.data[0].discountedCost").value(10));
    }

    @Test
    void shouldMeterByDiscountedCostAndRecordCostPoints() throws Exception {
        String token = registerAndLogin("meter_user_it");
        String plaintextKey = createKey(token);
        SysUser user = findUser("meter_user_it");
        assertNotNull(user);

        apiEndpointMetaService.updateCost(ECHO_ENDPOINT_ID, 10);
        grantPermission(user.getId(), ECHO_ENDPOINT_ID, "0.75");

        mockMvc.perform(get("/api/v1/services/echo")
                        .param("message", "metered")
                        .header(API_KEY_HEADER, plaintextKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("metered"));

        assertEquals(992L, sysUserService.getById(user.getId()).getBalanceQuota());
        assertEquals(1L, countLogs(user.getId()));
        assertEquals(8, latestCostPoints(user.getId()));
        assertEquals(1L, apiKeyInfoService.getByKeyHash(ApiKeyUtils.sha256(plaintextKey)).getUsedQuota());

        // 并发安全的兜底：余额低于实际点数时直接 402，不写日志
        SysUser lowBalance = sysUserService.getById(user.getId());
        lowBalance.setBalanceQuota(7L);
        sysUserService.updateById(lowBalance);

        mockMvc.perform(get("/api/v1/services/echo")
                        .param("message", "too-expensive")
                        .header(API_KEY_HEADER, plaintextKey))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value(40200));
        assertEquals(1L, countLogs(user.getId()));

        // 0 折免费特权：余额为 0 也可调用，日志记录 0 点
        UserApiPermission permission = findPermission(user.getId(), ECHO_ENDPOINT_ID);
        permission.setCustomDiscount(new BigDecimal("0.00"));
        userApiPermissionService.updateById(permission);

        SysUser zeroBalance = sysUserService.getById(user.getId());
        zeroBalance.setBalanceQuota(0L);
        sysUserService.updateById(zeroBalance);

        mockMvc.perform(get("/api/v1/services/echo")
                        .param("message", "free")
                        .header(API_KEY_HEADER, plaintextKey))
                .andExpect(status().isOk());
        assertEquals(0L, sysUserService.getById(user.getId()).getBalanceQuota());
        assertEquals(2L, countLogs(user.getId()));
        assertEquals(0, latestCostPoints(user.getId()));

        // 未获得权限的接口返回 403
        mockMvc.perform(post("/api/v1/services/text-stats")
                        .header(API_KEY_HEADER, plaintextKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("text", "no permission"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40304));
        assertEquals(2L, countLogs(user.getId()));

        // 权限被收回后不能再调用
        permission = findPermission(user.getId(), ECHO_ENDPOINT_ID);
        permission.setStatus(3);
        userApiPermissionService.updateById(permission);

        mockMvc.perform(get("/api/v1/services/echo")
                        .param("message", "revoked")
                        .header(API_KEY_HEADER, plaintextKey))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40304));
    }

    private String registerAndLogin(String username) throws Exception {
        register(username);
        return login(username);
    }

    private void register(String username) throws Exception {
        mockMvc.perform(post("/api/console/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", username,
                                "email", username + "@example.com",
                                "code", "123456",
                                "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private String createAdminToken(String username) throws Exception {
        register(username);
        SysUser user = findUser(username);
        user.setRole("ADMIN");
        sysUserService.updateById(user);
        return login(username);
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/console/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("accessToken").asString();
    }

    private String createKey(String token) throws Exception {
        String body = mockMvc.perform(post("/api/console/apikeys")
                        .header(AUTH_HEADER, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("keyName", "permission-test-key", "rateLimitQps", 10))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data").path("apiKey").asString();
    }

    private void grantPermission(Long userId, Long endpointId, String discount) {
        UserApiPermission permission = new UserApiPermission();
        permission.setUserId(userId);
        permission.setEndpointId(endpointId);
        permission.setApplyReason("integration test");
        permission.setStatus(1);
        permission.setCustomDiscount(new BigDecimal(discount));
        userApiPermissionService.save(permission);
    }

    private SysUser findUser(String username) {
        return sysUserService.lambdaQuery().eq(SysUser::getUsername, username).one();
    }

    private UserApiPermission findPermission(Long userId, Long endpointId) {
        return userApiPermissionService.lambdaQuery()
                .eq(UserApiPermission::getUserId, userId)
                .eq(UserApiPermission::getEndpointId, endpointId)
                .one();
    }

    private long countLogs(Long userId) {
        return apiAccessLogService.lambdaQuery()
                .eq(ApiAccessLog::getUserId, userId)
                .count();
    }

    private int latestCostPoints(Long userId) {
        List<ApiAccessLog> logs = apiAccessLogService.lambdaQuery()
                .eq(ApiAccessLog::getUserId, userId)
                .orderByDesc(ApiAccessLog::getId)
                .list();
        assertTrue(!logs.isEmpty());
        return logs.get(0).getCostPoints();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) {
        return objectMapper.writeValueAsString(value);
    }
}
