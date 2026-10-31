package com.example.api_service.integration;

import com.example.api_service.service.EmailService;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChangePasswordApiIntegrationTest {

    private static final String AUTH_HEADER = "Authorization";
    private static final String OLD_PASSWORD = "Str0ngPass2026";
    private static final String NEW_PASSWORD = "NewStr0ngPass2027";
    private static final String WRONG_PASSWORD = "WrongPass2026";
    /** 27 个字符但 75 个 UTF-8 字节：字符长度合规，BCrypt 的 72 字节上限会拒绝 */
    private static final String MULTIBYTE_OVER_LIMIT =
            "密文口令账号注册登录验证强度策略序列重复常见词典Ab1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** 集成测试不依赖真实 Redis / SMTP，验证码校验由该 Mock 通过 */
    @MockitoBean
    private EmailService emailService;

    @Test
    void shouldChangePasswordAndOnlyKeepCurrentDeviceSignedIn() throws Exception {
        String username = "changepw_ok";
        String oldToken = registerAndLogin(username, "changepw_ok@example.com");

        MvcResult result = mockMvc.perform(post("/api/console/auth/change-password")
                        .header(AUTH_HEADER, bearer(oldToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "oldPassword", OLD_PASSWORD,
                                "newPassword", NEW_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andReturn();
        String newToken = objectMapper.readTree(
                        result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("accessToken").asString();

        // 历史令牌因会话版本递增而全部失效
        mockMvc.perform(get("/api/console/auth/me").header(AUTH_HEADER, bearer(oldToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40106));

        // 当前设备拿到的新令牌保持可用
        mockMvc.perform(get("/api/console/auth/me").header(AUTH_HEADER, bearer(newToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username));

        // 旧密码不可登录，新密码可以
        mockMvc.perform(post("/api/console/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", OLD_PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40107));
        mockMvc.perform(post("/api/console/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", NEW_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void shouldLockAfterRepeatedWrongOldPassword() throws Exception {
        String token = registerAndLogin("changepw_lock", "changepw_lock@example.com");

        for (int attempt = 1; attempt <= 5; attempt++) {
            int expectedStatus = attempt < 5 ? 400 : 429;
            int expectedCode = attempt < 5 ? 40008 : 42905;
            mockMvc.perform(post("/api/console/auth/change-password")
                            .header(AUTH_HEADER, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "oldPassword", WRONG_PASSWORD,
                                    "newPassword", NEW_PASSWORD))))
                    .andExpect(status().is(expectedStatus))
                    .andExpect(jsonPath("$.code").value(expectedCode));
        }

        // 锁定期间即使旧密码正确也拒绝
        mockMvc.perform(post("/api/console/auth/change-password")
                        .header(AUTH_HEADER, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "oldPassword", OLD_PASSWORD,
                                "newPassword", NEW_PASSWORD))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(42905));
    }

    @Test
    void shouldRejectWeakOrSameNewPasswordWithoutConsumingAttempts() throws Exception {
        String username = "changepw_policy";
        String token = registerAndLogin(username, "changepw_policy@example.com");

        // 新密码不能与当前密码相同
        mockMvc.perform(post("/api/console/auth/change-password")
                        .header(AUTH_HEADER, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "oldPassword", OLD_PASSWORD,
                                "newPassword", OLD_PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40009));

        // 弱密码在旧密码校验之前拦截：缺少大写、包含用户名、常见弱口令、多字节超 72 字节
        assertWeakPasswordRejected(token, WRONG_PASSWORD, "alllowercase123");
        assertWeakPasswordRejected(token, WRONG_PASSWORD, "Changepw_policy2026");
        assertWeakPasswordRejected(token, OLD_PASSWORD, "Password123");
        assertWeakPasswordRejected(token, OLD_PASSWORD, MULTIBYTE_OVER_LIMIT);

        // 上述失败没有累计旧密码试错次数，正确旧密码仍可一次通过
        mockMvc.perform(post("/api/console/auth/change-password")
                        .header(AUTH_HEADER, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "oldPassword", OLD_PASSWORD,
                                "newPassword", NEW_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void shouldRejectWeakPasswordAtRegistration() throws Exception {
        mockMvc.perform(post("/api/console/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", "weakreg_user",
                                "email", "weakreg_user@example.com",
                                "code", "123456",
                                "password", "password123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40007));

        // 密码不合规时不消耗邮箱验证码
        verify(emailService, never()).verifyCode(anyString(), anyString());
    }

    @Test
    void shouldRejectMultiBytePasswordBeyondBcryptLimitAtRegistration() throws Exception {
        mockMvc.perform(post("/api/console/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", "weakreg_bytes",
                                "email", "weakreg_bytes@example.com",
                                "code", "123456",
                                "password", MULTIBYTE_OVER_LIMIT))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40007));

        // 策略在 BCrypt 编码与验证码消费之前拦下，避免 500 且不烧掉验证码
        verify(emailService, never()).verifyCode(anyString(), anyString());
    }

    private void assertWeakPasswordRejected(String token, String oldPassword, String newPassword)
            throws Exception {
        mockMvc.perform(post("/api/console/auth/change-password")
                        .header(AUTH_HEADER, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "oldPassword", oldPassword,
                                "newPassword", newPassword))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40007));
    }

    private String registerAndLogin(String username, String email) throws Exception {
        mockMvc.perform(post("/api/console/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", username,
                                "email", email,
                                "code", "123456",
                                "password", OLD_PASSWORD))))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(post("/api/console/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", OLD_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("accessToken").asString();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) {
        return objectMapper.writeValueAsString(value);
    }
}
