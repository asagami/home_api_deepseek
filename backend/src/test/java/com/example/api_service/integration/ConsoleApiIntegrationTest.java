package com.example.api_service.integration;

import com.example.api_service.model.entity.ApiAccessLog;
import com.example.api_service.model.entity.ApiKeyInfo;
import com.example.api_service.model.entity.SysUser;
import com.example.api_service.model.entity.UserApiPermission;
import com.example.api_service.security.ApiKeyUtils;
import com.example.api_service.service.ApiAccessLogService;
import com.example.api_service.service.ApiKeyInfoService;
import com.example.api_service.service.EmailService;
import com.example.api_service.service.SysUserService;
import com.example.api_service.service.UserApiPermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConsoleApiIntegrationTest {

    private static final String AUTH_HEADER = "Authorization";
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String PASSWORD = "password123";

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
    private PasswordEncoder passwordEncoder;

    /** 集成测试不依赖真实 Redis / SMTP，验证码校验由该 Mock 通过 */
    @MockitoBean
    private EmailService emailService;

    @Test
    void shouldSendEmailCodeWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/console/auth/send-email-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "alice@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(emailService).sendVerifyCode("alice@example.com");

        mockMvc.perform(post("/api/console/auth/send-email-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "not-an-email"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void shouldRegisterLoginAndReadProfile() throws Exception {
        mockMvc.perform(post("/api/console/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", "alice_it",
                                "email", "alice@example.com",
                                "code", "123456",
                                "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("alice_it"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.balanceQuota").value(1000));

        SysUser saved = findUser("alice_it");
        assertNotNull(saved);
        assertNotEquals(PASSWORD, saved.getPasswordHash());
        assertTrue(saved.getPasswordHash().startsWith("$2"));
        assertTrue(passwordEncoder.matches(PASSWORD, saved.getPasswordHash()));

        mockMvc.perform(post("/api/console/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", "alice_it",
                                "email", "alice@example.com",
                                "code", "123456",
                                "password", PASSWORD))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901));

        mockMvc.perform(post("/api/console/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "alice_it", "password", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40107));

        String token = login("alice_it");

        mockMvc.perform(get("/api/console/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40105));

        mockMvc.perform(get("/api/console/auth/me").header(AUTH_HEADER, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("alice_it"))
                .andExpect(jsonPath("$.data.balanceQuota").value(1000));
    }

    @Test
    void shouldCreateListDisableAndDeleteApiKey() throws Exception {
        registerAndLogin("bob_it");

        String createBody = mockMvc.perform(post("/api/console/apikeys")
                        .header(AUTH_HEADER, bearer(login("bob_it")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("keyName", "ci-key", "rateLimitQps", 3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.keyName").value("ci-key"))
                .andExpect(jsonPath("$.data.rateLimitQps").value(3))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode created = objectMapper.readTree(createBody).path("data");
        long keyId = created.path("id").asLong();
        String plaintextKey = created.path("apiKey").asString();
        assertTrue(plaintextKey.startsWith("sk-"));
        assertEquals(51, plaintextKey.length());
        assertTrue(created.path("keyPrefix").asString().contains("..."));

        ApiKeyInfo stored = apiKeyInfoService.getById(keyId);
        assertNotNull(stored);
        assertNotEquals(plaintextKey, stored.getKeyHash());
        assertEquals(ApiKeyUtils.sha256(plaintextKey), stored.getKeyHash());
        assertEquals(created.path("keyPrefix").asString(), stored.getKeyPrefix());

        String token = login("bob_it");
        mockMvc.perform(get("/api/console/apikeys")
                        .header(AUTH_HEADER, bearer(token))
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].keyPrefix").value(stored.getKeyPrefix()))
                .andExpect(jsonPath("$.data.records[0].apiKey").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].keyHash").doesNotExist());

        mockMvc.perform(get("/api/console/apikeys")
                        .header(AUTH_HEADER, bearer(token))
                        .param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));

        mockMvc.perform(patch("/api/console/apikeys/{id}/status", keyId)
                        .header(AUTH_HEADER, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", 0))))
                .andExpect(status().isOk());
        assertEquals(0, apiKeyInfoService.getById(keyId).getStatus());

        String otherToken = registerAndLogin("carol_it");
        mockMvc.perform(patch("/api/console/apikeys/{id}/status", keyId)
                        .header(AUTH_HEADER, bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", 1))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));

        mockMvc.perform(delete("/api/console/apikeys/{id}", keyId)
                        .header(AUTH_HEADER, bearer(token)))
                .andExpect(status().isOk());
        assertNull(apiKeyInfoService.getById(keyId));

        mockMvc.perform(delete("/api/console/apikeys/{id}", keyId)
                        .header(AUTH_HEADER, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));

        mockMvc.perform(get("/api/v1/services/echo")
                        .param("message", "hello")
                        .header(API_KEY_HEADER, plaintextKey))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40102));
    }

    @Test
    void shouldConsumeQuotaAndWriteAccessLogOnSuccessfulCalls() throws Exception {
        registerAndLogin("dave_it");
        String token = login("dave_it");
        String plaintextKey = createKey(token, "open-api-key");
        SysUser user = findUser("dave_it");
        assertNotNull(user);
        grantPermission(user.getId(), 1001L);
        grantPermission(user.getId(), 1002L);

        mockMvc.perform(get("/api/v1/services/echo")
                        .param("message", "hello")
                        .header(API_KEY_HEADER, plaintextKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.message").value("hello"));

        assertEquals(999L, sysUserService.getById(user.getId()).getBalanceQuota());
        assertTrue(apiKeyInfoService.getByKeyHash(ApiKeyUtils.sha256(plaintextKey)).getUsedQuota() == 1L);
        assertEquals(1L, countLogs(user.getId()));
        assertTrue(apiAccessLogService.lambdaQuery()
                .eq(ApiAccessLog::getUserId, user.getId())
                .eq(ApiAccessLog::getCostPoints, 1)
                .count() == 1L);

        mockMvc.perform(post("/api/v1/services/text-stats")
                        .header(API_KEY_HEADER, plaintextKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("text", "Hello world, hello API! hello"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.characterCount").value(29))
                .andExpect(jsonPath("$.data.wordCount").value(5))
                .andExpect(jsonPath("$.data.uniqueWordCount").value(3))
                .andExpect(jsonPath("$.data.topWords[0].word").value("hello"))
                .andExpect(jsonPath("$.data.topWords[0].count").value(3));

        assertEquals(998L, sysUserService.getById(user.getId()).getBalanceQuota());
        assertEquals(2L, countLogs(user.getId()));
        assertTrue(apiAccessLogService.lambdaQuery()
                .eq(ApiAccessLog::getUserId, user.getId())
                .eq(ApiAccessLog::getCostPoints, 1)
                .count() == 2L);

        SysUser exhausted = sysUserService.getById(user.getId());
        exhausted.setBalanceQuota(0L);
        sysUserService.updateById(exhausted);

        mockMvc.perform(get("/api/v1/services/echo")
                        .param("message", "blocked")
                        .header(API_KEY_HEADER, plaintextKey))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value(40200));
        assertEquals(2L, countLogs(user.getId()));

        mockMvc.perform(get("/api/v1/services/echo").param("message", "no-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    private String registerAndLogin(String username) throws Exception {
        mockMvc.perform(post("/api/console/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", username,
                                "email", username + "@example.com",
                                "code", "123456",
                                "password", PASSWORD))))
                .andExpect(status().isOk());
        return login(username);
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/console/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("accessToken").asString();
    }

    private String createKey(String token, String keyName) throws Exception {
        String body = mockMvc.perform(post("/api/console/apikeys")
                        .header(AUTH_HEADER, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("keyName", keyName, "rateLimitQps", 10))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data").path("apiKey").asString();
    }

    private SysUser findUser(String username) {
        return sysUserService.lambdaQuery().eq(SysUser::getUsername, username).one();
    }

    private long countLogs(Long userId) {
        return apiAccessLogService.lambdaQuery()
                .eq(ApiAccessLog::getUserId, userId)
                .count();
    }

    private void grantPermission(Long userId, Long endpointId) {
        UserApiPermission permission = new UserApiPermission();
        permission.setUserId(userId);
        permission.setEndpointId(endpointId);
        permission.setApplyReason("integration test");
        permission.setStatus(1);
        permission.setCustomDiscount(BigDecimal.ONE);
        userApiPermissionService.save(permission);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) {
        return objectMapper.writeValueAsString(value);
    }
}
