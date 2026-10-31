package com.example.api_service.integration;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.api_service.mapper.PasswordResetTokenMapper;
import com.example.api_service.model.entity.PasswordResetToken;
import com.example.api_service.model.entity.SysUser;
import com.example.api_service.security.ResetTokenCodec;
import com.example.api_service.service.EmailService;
import com.example.api_service.service.SysUserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PasswordResetApiIntegrationTest {

    private static final String AUTH_HEADER = "Authorization";
    private static final String OLD_PASSWORD = "Str0ngPass2026";
    private static final String NEW_PASSWORD = "NewStr0ngPass2027";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private PasswordResetTokenMapper passwordResetTokenMapper;

    @Autowired
    private ResetTokenCodec resetTokenCodec;

    /** 集成测试不依赖真实 SMTP，重置邮件由该 Mock 截获 */
    @MockitoBean
    private EmailService emailService;

    @Test
    void shouldReturnUniformResponseAndStoreOnlyTokenHash() throws Exception {
        register("reset_mail", "reset_mail@example.com");

        // 账号不存在也返回同样的成功响应，且不会发信
        mockMvc.perform(post("/api/console/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("account", "nobody_reset"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString(), anyLong());

        mockMvc.perform(post("/api/console/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("account", "reset_mail"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        // 邮件在独立线程池异步发送，等待发送调用到达
        verify(emailService, timeout(2000)).sendPasswordResetEmail(
                eq("reset_mail@example.com"), urlCaptor.capture(), eq(30L));
        String url = urlCaptor.getValue();
        assertTrue(url.startsWith("http://localhost:5173/reset-password?token="));
        String rawToken = url.substring(url.indexOf("token=") + "token=".length());

        SysUser user = findUser("reset_mail");
        PasswordResetToken row = passwordResetTokenMapper.selectOne(
                Wrappers.<PasswordResetToken>lambdaQuery()
                        .eq(PasswordResetToken::getUserId, user.getId()));
        assertNotNull(row);
        assertEquals(64, row.getTokenHash().length());
        assertEquals(resetTokenCodec.hash(rawToken), row.getTokenHash());
        assertNotEquals(rawToken, row.getTokenHash());
    }

    @Test
    void shouldVerifyOnceResetPasswordAndRevokeExistingSessions() throws Exception {
        String username = "reset_flow";
        String email = "reset_flow@example.com";
        String oldToken = registerAndLogin(username, email);

        String firstToken = requestResetAndCaptureToken(username, email);
        String secondToken = requestResetAndCaptureToken(username, email);
        String thirdToken = requestResetAndCaptureToken(username, email);

        // 篡改签名直接拒绝，无需查库
        mockMvc.perform(post("/api/console/auth/reset-password/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", firstToken + "x"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40005));

        // 校验通过换票，并返回脱敏邮箱
        MvcResult verified = mockMvc.perform(post("/api/console/auth/reset-password/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", firstToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.ticket").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresInSeconds").value(600))
                .andExpect(jsonPath("$.data.maskedEmail").value("r***@example.com"))
                .andReturn();
        String ticket = objectMapper.readTree(
                        verified.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("ticket").asString();

        // 邮件令牌一次性：换票后再次校验失败
        mockMvc.perform(post("/api/console/auth/reset-password/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", firstToken))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40005));

        // 重置前提前换出的第二张票据：会话版本将在重置时递增，该票据应随之失效
        String staleTicket = verifyTokenAndCaptureTicket(secondToken);

        // 伪造票据、弱密码分别被拒绝
        mockMvc.perform(post("/api/console/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("ticket", "bogus", "newPassword", NEW_PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40006));
        mockMvc.perform(post("/api/console/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("ticket", ticket, "newPassword", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));

        // 正常重置
        mockMvc.perform(post("/api/console/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("ticket", ticket, "newPassword", NEW_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 重置前换出的旧票据被会话版本拦截，无法再次重置密码
        mockMvc.perform(post("/api/console/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("ticket", staleTicket, "newPassword", NEW_PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40006));

        // 票据复用被拒绝
        mockMvc.perform(post("/api/console/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("ticket", ticket, "newPassword", NEW_PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40006));

        // 历史 JWT 因会话版本递增而全部失效
        mockMvc.perform(get("/api/console/auth/me").header(AUTH_HEADER, bearer(oldToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40106));

        // 旧密码不可登录，新密码可登录
        mockMvc.perform(post("/api/console/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", OLD_PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40107));
        mockMvc.perform(post("/api/console/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", NEW_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));

        // 重置成功后，其余未使用的重置令牌一并作废
        mockMvc.perform(post("/api/console/auth/reset-password/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", thirdToken))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40005));
    }

    @Test
    void shouldKeepTicketWhenNewPasswordViolatesPolicy() throws Exception {
        String username = "reset_policy";
        String email = "reset_policy@example.com";
        register(username, email);

        String rawToken = requestResetAndCaptureToken(username, email);
        String ticket = verifyTokenAndCaptureTicket(rawToken);

        // 27 个字符但 75 个 UTF-8 字节：策略拒绝，票据写回，允许用同一票据重试
        mockMvc.perform(post("/api/console/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("ticket", ticket, "newPassword",
                                "密文口令账号注册登录验证强度策略序列重复常见词典Ab1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40007));

        mockMvc.perform(post("/api/console/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("ticket", ticket, "newPassword", NEW_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private String registerAndLogin(String username, String email) throws Exception {
        register(username, email);
        MvcResult result = mockMvc.perform(post("/api/console/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", OLD_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("accessToken").asString();
    }

    private void register(String username, String email) throws Exception {
        mockMvc.perform(post("/api/console/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", username,
                                "email", email,
                                "code", "123456",
                                "password", OLD_PASSWORD))))
                .andExpect(status().isOk());
    }

    private String requestResetAndCaptureToken(String username, String email) throws Exception {
        clearInvocations(emailService);
        mockMvc.perform(post("/api/console/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("account", username))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService, timeout(2000))
                .sendPasswordResetEmail(eq(email), urlCaptor.capture(), anyLong());
        String url = urlCaptor.getValue();
        return url.substring(url.indexOf("token=") + "token=".length());
    }

    private String verifyTokenAndCaptureTicket(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/console/auth/reset-password/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("ticket").asString();
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
