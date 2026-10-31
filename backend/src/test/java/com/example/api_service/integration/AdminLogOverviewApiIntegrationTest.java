package com.example.api_service.integration;

import com.example.api_service.model.entity.ApiAccessLog;
import com.example.api_service.model.entity.ApiKeyInfo;
import com.example.api_service.model.entity.SysUser;
import com.example.api_service.service.ApiAccessLogService;
import com.example.api_service.service.ApiKeyInfoService;
import com.example.api_service.service.EmailService;
import com.example.api_service.service.SysUserService;
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

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminLogOverviewApiIntegrationTest {

    private static final String AUTH_HEADER = "Authorization";
    private static final String PASSWORD = "Str0ngPass2026";
    private static final String ENDPOINT_ECHO = "/api/v1/services/echo";
    private static final String ENDPOINT_TEXT_STATS = "/api/v1/services/text-stats";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private ApiAccessLogService apiAccessLogService;

    @Autowired
    private ApiKeyInfoService apiKeyInfoService;

    /** 集成测试不依赖真实 Redis / SMTP */
    @MockitoBean
    private EmailService emailService;

    @Test
    void shouldRejectNonAdminOnAdminLogEndpoints() throws Exception {
        String token = registerAndLogin("admin_log_plain");

        mockMvc.perform(post("/api/console/admin/logs/overview")
                        .header(AUTH_HEADER, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));

        mockMvc.perform(post("/api/console/admin/logs/page")
                        .header(AUTH_HEADER, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    void shouldAggregateOverviewAcrossAllUsersAndEndpoints() throws Exception {
        register("admin_log_owner");
        register("admin_log_alice");
        register("admin_log_bob");
        String adminToken = createAdminAndLogin("admin_log_owner");

        Long aliceId = findUser("admin_log_alice").getId();
        Long bobId = findUser("admin_log_bob").getId();
        long aliceKeyId = 9_876_543_211L;
        long bobKeyId = 9_876_543_212L;
        insertKey(aliceKeyId, aliceId, "sk-alice...a1");
        insertKey(bobKeyId, bobId, "sk-bob...b2");

        LocalDate today = LocalDate.now();
        LocalDateTime todayMorning = today.atTime(10, 0);
        LocalDateTime yesterdayNoon = today.minusDays(1).atTime(12, 0);

        insertLog(aliceId, aliceKeyId, ENDPOINT_ECHO, 200, 2, 10, todayMorning);
        insertLog(aliceId, aliceKeyId, ENDPOINT_TEXT_STATS, 429, 0, 20, todayMorning.plusHours(1));
        insertLog(bobId, bobKeyId, ENDPOINT_ECHO, 500, 1, 30, yesterdayNoon);
        insertLog(bobId, bobKeyId, ENDPOINT_TEXT_STATS, 200, 3, 40, yesterdayNoon.plusHours(1));
        // 窗口外数据：必须被 create_time 条件过滤掉
        insertLog(aliceId, aliceKeyId, ENDPOINT_ECHO, 200, 9, 999, today.minusDays(10).atTime(9, 0));

        LocalDateTime rangeStart = today.minusDays(6).atStartOfDay();
        LocalDateTime rangeEnd = today.atTime(23, 59, 59);

        mockMvc.perform(post("/api/console/admin/logs/overview")
                        .header(AUTH_HEADER, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "startTime", rangeStart.toString(),
                                "endTime", rangeEnd.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalCalls").value(4))
                .andExpect(jsonPath("$.data.totalCostPoints").value(6))
                .andExpect(jsonPath("$.data.successRate").value(50.0))
                .andExpect(jsonPath("$.data.rateLimitCount").value(1))
                .andExpect(jsonPath("$.data.error5xxCount").value(1))
                .andExpect(jsonPath("$.data.avgLatencyMs").value(25))
                .andExpect(jsonPath("$.data.dailyTrend.length()").value(7))
                .andExpect(jsonPath("$.data.dailyTrend[6].date").value(today.toString()))
                .andExpect(jsonPath("$.data.dailyTrend[6].totalCalls").value(2))
                .andExpect(jsonPath("$.data.dailyTrend[5].date").value(today.minusDays(1).toString()))
                .andExpect(jsonPath("$.data.dailyTrend[5].totalCalls").value(2))
                .andExpect(jsonPath("$.data.endpointRank.length()").value(2))
                .andExpect(jsonPath("$.data.topConsumeUsers.length()").value(2))
                // Bob 消耗 1+3=4 点，Alice 仅统计窗口内 2+0=2 点
                .andExpect(jsonPath("$.data.topConsumeUsers[0].username").value("admin_log_bob"))
                .andExpect(jsonPath("$.data.topConsumeUsers[0].costPoints").value(4))
                .andExpect(jsonPath("$.data.topConsumeUsers[0].userId").isString());

        // 空请求体走默认近 7 天
        mockMvc.perform(post("/api/console/admin/logs/overview")
                        .header(AUTH_HEADER, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dailyTrend.length()").value(7))
                .andExpect(jsonPath("$.data.dailyTrend[6].date").value(today.toString()));
    }

    @Test
    void shouldPageAllUsersLogsWithJoinedFields() throws Exception {
        register("admin_log_page_owner");
        register("admin_log_page_alice");
        String adminToken = createAdminAndLogin("admin_log_page_owner");

        Long aliceId = findUser("admin_log_page_alice").getId();
        long aliceKeyId = 9_876_543_221L;
        insertKey(aliceKeyId, aliceId, "sk-page...a1");

        LocalDate today = LocalDate.now();
        insertLog(aliceId, aliceKeyId, ENDPOINT_ECHO, 200, 2, 10, today.atTime(10, 0));
        insertLog(aliceId, aliceKeyId, ENDPOINT_TEXT_STATS, 429, 0, 20, today.atTime(11, 0));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("startTime", today.atStartOfDay().toString());
        request.put("endTime", today.atTime(23, 59, 59).toString());
        // 雪花 ID 前端以字符串传递，Jackson 需能反序列化为 Long
        request.put("userId", String.valueOf(aliceId));
        request.put("username", "admin_log_page_alice");
        request.put("endpointPath", ENDPOINT_ECHO);
        request.put("httpStatus", 200);
        request.put("minLatencyMs", 5);
        request.put("pageNum", 1);
        request.put("pageSize", 10);

        mockMvc.perform(post("/api/console/admin/logs/page")
                        .header(AUTH_HEADER, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.pageNum").value(1))
                .andExpect(jsonPath("$.data.records[0].username").value("admin_log_page_alice"))
                .andExpect(jsonPath("$.data.records[0].keyPrefix").value("sk-page...a1"))
                .andExpect(jsonPath("$.data.records[0].endpointName").value("回声测试"))
                .andExpect(jsonPath("$.data.records[0].endpointPath").value(ENDPOINT_ECHO))
                .andExpect(jsonPath("$.data.records[0].httpStatus").value(200))
                .andExpect(jsonPath("$.data.records[0].costPoints").value(2))
                .andExpect(jsonPath("$.data.records[0].userId").isString())
                .andExpect(jsonPath("$.data.records[0].apiKeyId").isString());

        // 不筛选时按 create_time 倒序，最新的 429 记录排第一
        Map<String, Object> unfiltered = new LinkedHashMap<>();
        unfiltered.put("startTime", today.atStartOfDay().toString());
        unfiltered.put("endTime", today.atTime(23, 59, 59).toString());
        mockMvc.perform(post("/api/console/admin/logs/page")
                        .header(AUTH_HEADER, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(unfiltered)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[0].httpStatus").value(429));
    }

    @Test
    void shouldRequireTimeRangeAndRejectInvalidSpans() throws Exception {
        register("admin_log_validate_owner");
        String adminToken = createAdminAndLogin("admin_log_validate_owner");

        mockMvc.perform(post("/api/console/admin/logs/page")
                        .header(AUTH_HEADER, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));

        LocalDate today = LocalDate.now();
        mockMvc.perform(post("/api/console/admin/logs/page")
                        .header(AUTH_HEADER, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "startTime", today.atTime(23, 0).toString(),
                                "endTime", today.atTime(1, 0).toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));

        mockMvc.perform(post("/api/console/admin/logs/overview")
                        .header(AUTH_HEADER, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "startTime", today.minusDays(40).atStartOfDay().toString(),
                                "endTime", today.atTime(23, 59, 59).toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    private void insertKey(long keyId, Long userId, String keyPrefix) {
        ApiKeyInfo key = new ApiKeyInfo();
        key.setId(keyId);
        key.setUserId(userId);
        key.setKeyName("admin-log-test");
        key.setKeyPrefix(keyPrefix);
        key.setKeyHash("hash-" + keyId);
        key.setRateLimitQps(5);
        key.setStatus(1);
        apiKeyInfoService.save(key);
    }

    private void insertLog(Long userId, long apiKeyId, String endpointPath, int httpStatus,
                           int costPoints, int latencyMs, LocalDateTime createTime) {
        ApiAccessLog accessLog = new ApiAccessLog();
        accessLog.setUserId(userId);
        accessLog.setApiKeyId(apiKeyId);
        accessLog.setEndpointPath(endpointPath);
        accessLog.setHttpStatus(httpStatus);
        accessLog.setCostPoints(costPoints);
        accessLog.setLatencyMs(latencyMs);
        accessLog.setClientIp("127.0.0.1");
        accessLog.setCreateTime(createTime);
        apiAccessLogService.save(accessLog);
    }

    private String createAdminAndLogin(String username) throws Exception {
        SysUser user = findUser(username);
        user.setRole("ADMIN");
        sysUserService.updateById(user);
        return login(username);
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
                .andExpect(status().isOk());
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/console/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("accessToken").asString();
    }

    private SysUser findUser(String username) {
        return sysUserService.lambdaQuery().eq(SysUser::getUsername, username).one();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) {
        return objectMapper.writeValueAsString(value);
    }
}
