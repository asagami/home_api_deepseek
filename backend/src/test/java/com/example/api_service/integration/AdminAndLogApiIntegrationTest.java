package com.example.api_service.integration;

import com.example.api_service.model.entity.ApiAccessLog;
import com.example.api_service.model.entity.SysUser;
import com.example.api_service.service.ApiAccessLogService;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminAndLogApiIntegrationTest {

    private static final String AUTH_HEADER = "Authorization";
    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private ApiAccessLogService apiAccessLogService;

    /** 集成测试不依赖真实 Redis / SMTP，验证码校验由该 Mock 通过 */
    @MockitoBean
    private EmailService emailService;

    @Test
    void shouldRejectNonAdminOnAdminEndpoints() throws Exception {
        String token = registerAndLogin("plain_user_it", null);

        mockMvc.perform(get("/api/console/admin/users").header(AUTH_HEADER, bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    void shouldListSearchBanUnbanAndRechargeUsers() throws Exception {
        register("target_user_it", "target@example.com");
        String adminToken = createAdminToken("admin_it");

        SysUser target = findUser("target_user_it");
        long targetId = target.getId();

        mockMvc.perform(get("/api/console/admin/users")
                        .header(AUTH_HEADER, bearer(adminToken))
                        .param("keyword", "target_user_it"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].username").value("target_user_it"))
                .andExpect(jsonPath("$.data.records[0].email").value("target@example.com"))
                // 雪花 ID 必须以字符串返回，否则 JS 端会丢失精度导致封禁/充值命中错误用户
                .andExpect(jsonPath("$.data.records[0].id").isString())
                .andExpect(jsonPath("$.data.records[0].id").value(String.valueOf(targetId)))
                .andExpect(jsonPath("$.data.records[0].balanceQuota").value(1000))
                .andExpect(jsonPath("$.data.records[0].role").value("USER"));

        mockMvc.perform(post("/api/console/admin/users/{id}/quota", targetId)
                        .header(AUTH_HEADER, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amount", 500))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.balanceQuota").value(1500));
        assertEquals(1500L, sysUserService.getById(targetId).getBalanceQuota());

        mockMvc.perform(post("/api/console/admin/users/{id}/quota", targetId)
                        .header(AUTH_HEADER, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amount", 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));

        mockMvc.perform(post("/api/console/admin/users/{id}/quota", 999_999_999L)
                        .header(AUTH_HEADER, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amount", 100))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40402));

        mockMvc.perform(patch("/api/console/admin/users/{id}/status", targetId)
                        .header(AUTH_HEADER, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", 0))))
                .andExpect(status().isOk());
        assertEquals(0, sysUserService.getById(targetId).getStatus());

        mockMvc.perform(post("/api/console/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "target_user_it", "password", PASSWORD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        mockMvc.perform(get("/api/console/admin/users")
                        .header(AUTH_HEADER, bearer(adminToken))
                        .param("keyword", "target_user_it")
                        .param("status", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].status").value(0));

        mockMvc.perform(patch("/api/console/admin/users/{id}/status", targetId)
                        .header(AUTH_HEADER, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", 1))))
                .andExpect(status().isOk());
        assertEquals(1, sysUserService.getById(targetId).getStatus());

        long adminId = findUser("admin_it").getId();
        mockMvc.perform(patch("/api/console/admin/users/{id}/status", adminId)
                        .header(AUTH_HEADER, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", 0))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40303));
    }

    @Test
    void shouldPageLogsByTimeRangeAndAggregateLastSevenDays() throws Exception {
        String token = registerAndLogin("log_user_it", null);
        Long userId = findUser("log_user_it").getId();

        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        insertLog(userId, 200, 10, today.atTime(10, 0));
        insertLog(userId, 500, 30, today.atTime(11, 0));
        insertLog(userId, 429, 20, yesterday.atTime(12, 0));
        // 7 天窗口之外，用于验证 create_time 下界过滤
        insertLog(userId, 200, 40, today.minusDays(8).atTime(9, 0));

        mockMvc.perform(get("/api/console/logs")
                        .header(AUTH_HEADER, bearer(token))
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records[0].httpStatus").value(500))
                .andExpect(jsonPath("$.data.records[0].latencyMs").value(30));

        mockMvc.perform(get("/api/console/logs")
                        .header(AUTH_HEADER, bearer(token))
                        .param("httpStatus", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].httpStatus").value(200));

        mockMvc.perform(get("/api/console/logs")
                        .header(AUTH_HEADER, bearer(token))
                        .param("startTime", yesterday.atStartOfDay().toString())
                        .param("endTime", yesterday.atTime(23, 59, 59).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].httpStatus").value(429));

        mockMvc.perform(get("/api/console/logs")
                        .header(AUTH_HEADER, bearer(token))
                        .param("startTime", today.atStartOfDay().toString())
                        .param("endTime", yesterday.atTime(23, 59, 59).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));

        mockMvc.perform(get("/api/console/logs/stats").header(AUTH_HEADER, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startDate").value(today.minusDays(6).toString()))
                .andExpect(jsonPath("$.data.endDate").value(today.toString()))
                .andExpect(jsonPath("$.data.totalCalls").value(3))
                .andExpect(jsonPath("$.data.successCalls").value(1))
                .andExpect(jsonPath("$.data.clientErrorCalls").value(1))
                .andExpect(jsonPath("$.data.serverErrorCalls").value(1))
                .andExpect(jsonPath("$.data.avgLatencyMs").value(20))
                .andExpect(jsonPath("$.data.successRate").value(33.3))
                .andExpect(jsonPath("$.data.daily.length()").value(7))
                .andExpect(jsonPath("$.data.daily[6].date").value(today.toString()))
                .andExpect(jsonPath("$.data.daily[6].totalCalls").value(2))
                .andExpect(jsonPath("$.data.daily[5].date").value(yesterday.toString()))
                .andExpect(jsonPath("$.data.daily[5].totalCalls").value(1));

        mockMvc.perform(get("/api/console/logs/stats")
                        .header(AUTH_HEADER, bearer(token))
                        .param("startDate", today.toString())
                        .param("endDate", yesterday.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    private void insertLog(Long userId, int httpStatus, int latencyMs, LocalDateTime createTime) {
        ApiAccessLog accessLog = new ApiAccessLog();
        accessLog.setUserId(userId);
        accessLog.setApiKeyId(1L);
        accessLog.setEndpointPath("/api/v1/services/echo");
        accessLog.setHttpStatus(httpStatus);
        accessLog.setLatencyMs(latencyMs);
        accessLog.setClientIp("127.0.0.1");
        accessLog.setCreateTime(createTime);
        apiAccessLogService.save(accessLog);
    }

    private String createAdminToken(String username) throws Exception {
        register(username, null);
        SysUser user = findUser(username);
        user.setRole("ADMIN");
        sysUserService.updateById(user);
        return login(username);
    }

    private String registerAndLogin(String username, String email) throws Exception {
        register(username, email);
        return login(username);
    }

    private void register(String username, String email) throws Exception {
        String body = json(Map.of(
                "username", username,
                "email", email == null ? username + "@example.com" : email,
                "code", "123456",
                "password", PASSWORD));
        mockMvc.perform(post("/api/console/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
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
