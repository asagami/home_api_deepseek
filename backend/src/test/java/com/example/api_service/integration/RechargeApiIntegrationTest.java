package com.example.api_service.integration;

import com.example.api_service.model.entity.RechargeOrder;
import com.example.api_service.model.entity.SysUser;
import com.example.api_service.service.EmailService;
import com.example.api_service.service.RechargeOrderService;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RechargeApiIntegrationTest {

    private static final String AUTH_HEADER = "Authorization";
    private static final String PASSWORD = "Str0ngPass2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private RechargeOrderService rechargeOrderService;

    /** 集成测试不依赖真实 Redis / SMTP，验证码校验由该 Mock 通过 */
    @MockitoBean
    private EmailService emailService;

    @Test
    void shouldExposeFivePackagesAndValidateSelection() throws Exception {
        String token = registerAndLogin("recharge_pkg_it");

        mockMvc.perform(get("/api/console/recharge/packages"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40105));

        mockMvc.perform(get("/api/console/recharge/packages").header(AUTH_HEADER, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[0].packageId").value("PKG_10"))
                .andExpect(jsonPath("$.data[0].amountFen").value(1000))
                .andExpect(jsonPath("$.data[0].basePoints").value(1000))
                .andExpect(jsonPath("$.data[0].bonusPoints").value(0))
                .andExpect(jsonPath("$.data[0].totalPoints").value(1000))
                .andExpect(jsonPath("$.data[1].amountFen").value(2000))
                .andExpect(jsonPath("$.data[1].totalPoints").value(2200))
                .andExpect(jsonPath("$.data[2].totalPoints").value(6000))
                .andExpect(jsonPath("$.data[3].totalPoints").value(13000))
                .andExpect(jsonPath("$.data[3].hot").value(true))
                .andExpect(jsonPath("$.data[3].bestValue").value(false))
                .andExpect(jsonPath("$.data[4].totalPoints").value(30000))
                .andExpect(jsonPath("$.data[4].hot").value(false))
                .andExpect(jsonPath("$.data[4].bestValue").value(true));

        mockMvc.perform(post("/api/console/recharge/create-order")
                        .header(AUTH_HEADER, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("packageId", "PKG_999", "payChannel", "ALIPAY"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40002));

        mockMvc.perform(post("/api/console/recharge/create-order")
                        .header(AUTH_HEADER, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("packageId", "PKG_10", "payChannel", "UNIONPAY"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40003));

        mockMvc.perform(post("/api/console/recharge/create-order")
                        .header(AUTH_HEADER, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("packageId", "", "payChannel", "ALIPAY"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void shouldCreateQueryAndPayOrder() throws Exception {
        String token = registerAndLogin("recharge_flow_it");
        SysUser user = findUser("recharge_flow_it");

        String createBody = mockMvc.perform(post("/api/console/recharge/create-order")
                        .header(AUTH_HEADER, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("packageId", "PKG_20", "payChannel", "WECHAT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.packageId").value("PKG_20"))
                .andExpect(jsonPath("$.data.amountFen").value(2000))
                .andExpect(jsonPath("$.data.basePoints").value(2000))
                .andExpect(jsonPath("$.data.bonusPoints").value(200))
                .andExpect(jsonPath("$.data.totalPoints").value(2200))
                .andExpect(jsonPath("$.data.payChannel").value("WECHAT"))
                .andExpect(jsonPath("$.data.payChannelName").value("微信支付"))
                .andExpect(jsonPath("$.data.status").value(0))
                .andExpect(jsonPath("$.data.statusDesc").value("待支付"))
                .andExpect(jsonPath("$.data.qrCodeUrl").isString())
                .andExpect(jsonPath("$.data.expireTime").exists())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode created = objectMapper.readTree(createBody).path("data");
        String orderNo = created.path("orderNo").asString();
        String qrCodeUrl = created.path("qrCodeUrl").asString();
        assertTrue(qrCodeUrl.startsWith("https://pay.example.com/qr?"));
        assertTrue(qrCodeUrl.contains("orderNo=" + orderNo));
        assertTrue(qrCodeUrl.contains("channel=WECHAT"));

        mockMvc.perform(get("/api/console/recharge/status/{orderNo}", orderNo)
                        .header(AUTH_HEADER, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.status").value(0))
                .andExpect(jsonPath("$.data.totalPoints").value(2200))
                .andExpect(jsonPath("$.data.balanceQuota").value(1000))
                .andExpect(jsonPath("$.data.tradeNo").doesNotExist());

        mockMvc.perform(post("/api/console/recharge/orders")
                        .header(AUTH_HEADER, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.records[0].status").value(0))
                .andExpect(jsonPath("$.data.records[0].amountFen").value(2000));

        mockMvc.perform(post("/api/console/recharge/orders")
                        .header(AUTH_HEADER, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(post("/api/console/recharge/orders")
                        .header(AUTH_HEADER, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pageSize", 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));

        mockMvc.perform(post("/api/console/recharge/mock-pay/{orderNo}", orderNo)
                        .header(AUTH_HEADER, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.statusDesc").value("已支付"))
                .andExpect(jsonPath("$.data.balanceQuota").value(3200))
                .andExpect(jsonPath("$.data.tradeNo").isString())
                .andExpect(jsonPath("$.data.payTime").exists());

        RechargeOrder paid = findOrder(orderNo);
        assertEquals(1, paid.getStatus());
        assertNotNull(paid.getTradeNo());
        assertTrue(paid.getTradeNo().startsWith("MOCK_"));
        assertNotNull(paid.getPayTime());
        assertEquals(3200L, sysUserService.getById(user.getId()).getBalanceQuota());

        mockMvc.perform(post("/api/console/recharge/mock-pay/{orderNo}", orderNo)
                        .header(AUTH_HEADER, bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40904));
        assertEquals(3200L, sysUserService.getById(user.getId()).getBalanceQuota());

        mockMvc.perform(get("/api/console/recharge/status/{orderNo}", orderNo)
                        .header(AUTH_HEADER, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.balanceQuota").value(3200));

        String otherToken = registerAndLogin("recharge_other_it");
        mockMvc.perform(get("/api/console/recharge/status/{orderNo}", orderNo)
                        .header(AUTH_HEADER, bearer(otherToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40405));

        mockMvc.perform(post("/api/console/recharge/mock-pay/{orderNo}", orderNo)
                        .header(AUTH_HEADER, bearer(otherToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40405));
    }

    @Test
    void shouldAutoCloseExpiredOrderAndRejectPayment() throws Exception {
        String token = registerAndLogin("recharge_expire_it");
        SysUser user = findUser("recharge_expire_it");

        RechargeOrder expired = newOrder(user.getId(), "R-expired-1", LocalDateTime.now().minusMinutes(1));
        rechargeOrderService.save(expired);

        mockMvc.perform(get("/api/console/recharge/status/{orderNo}", expired.getOrderNo())
                        .header(AUTH_HEADER, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(2))
                .andExpect(jsonPath("$.data.statusDesc").value("已关闭"))
                .andExpect(jsonPath("$.data.balanceQuota").value(1000));
        assertEquals(2, findOrder(expired.getOrderNo()).getStatus());

        mockMvc.perform(post("/api/console/recharge/mock-pay/{orderNo}", expired.getOrderNo())
                        .header(AUTH_HEADER, bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40905));
        assertEquals(1000L, sysUserService.getById(user.getId()).getBalanceQuota());

        RechargeOrder pendingExpired =
                newOrder(user.getId(), "R-expired-2", LocalDateTime.now().minusSeconds(30));
        rechargeOrderService.save(pendingExpired);

        mockMvc.perform(post("/api/console/recharge/mock-pay/{orderNo}", pendingExpired.getOrderNo())
                        .header(AUTH_HEADER, bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40906));
        assertEquals(0, findOrder(pendingExpired.getOrderNo()).getStatus());
        assertEquals(1000L, sysUserService.getById(user.getId()).getBalanceQuota());
    }

    private RechargeOrder newOrder(Long userId, String orderNo, LocalDateTime expireTime) {
        RechargeOrder order = new RechargeOrder();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setPackageId("PKG_10");
        order.setAmountFen(1000);
        order.setTotalPoints(1000L);
        order.setPayChannel("ALIPAY");
        order.setStatus(0);
        order.setQrCodeUrl("https://pay.example.com/qr?orderNo=" + orderNo + "&channel=ALIPAY");
        order.setExpireTime(expireTime);
        order.setIsDeleted(0);
        return order;
    }

    private RechargeOrder findOrder(String orderNo) {
        return rechargeOrderService.lambdaQuery()
                .eq(RechargeOrder::getOrderNo, orderNo)
                .one();
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
