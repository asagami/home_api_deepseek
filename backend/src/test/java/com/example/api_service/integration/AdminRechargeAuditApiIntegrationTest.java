package com.example.api_service.integration;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.api_service.mapper.AdminRechargeMonthlyUsageMapper;
import com.example.api_service.model.entity.AdminRechargeMonthlyUsage;
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
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminRechargeAuditApiIntegrationTest {

    private static final String AUTH_HEADER = "Authorization";
    private static final String PASSWORD = "Str0ngPass2026";
    private static final String BUSINESS_MONTH = YearMonth.now(ZoneId.of("Asia/Shanghai")).toString();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private RechargeOrderService rechargeOrderService;

    @Autowired
    private AdminRechargeMonthlyUsageMapper usageMapper;

    /** 集成测试不依赖真实 Redis / SMTP，验证码校验由该 Mock 通过 */
    @MockitoBean
    private EmailService emailService;

    @Test
    void shouldRecordAdminGrantAndEnforceMonthlyLimitPerAdminAndTarget() throws Exception {
        register("grant_target_it", "grant_target_it@example.com");
        String targetToken = login("grant_target_it");
        long targetId = findUser("grant_target_it").getId();
        String admin1Token = createAdminToken("grant_admin_1");
        String admin2Token = createAdminToken("grant_admin_2");
        long admin1Id = findUser("grant_admin_1").getId();

        // 第一笔管理员充值：写单 + 加点 + 备注
        mockMvc.perform(post("/api/console/admin/users/{id}/quota", targetId)
                        .header(AUTH_HEADER, bearer(admin1Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amount", 1000, "remark", "活动补偿"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.balanceQuota").value(2000));

        // 管理员视角：订单号、来源、操作人快照与备注都可查
        MvcResult recordsResult = mockMvc.perform(get("/api/console/admin/users/{id}/recharge-orders", targetId)
                        .header(AUTH_HEADER, bearer(admin1Token))
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].source").value(2))
                .andExpect(jsonPath("$.data.records[0].sourceDesc").value("管理员发放"))
                .andExpect(jsonPath("$.data.records[0].statusDesc").value("已到账"))
                .andExpect(jsonPath("$.data.records[0].payChannelName").value("管理员发放"))
                .andExpect(jsonPath("$.data.records[0].amountFen").value(0))
                .andExpect(jsonPath("$.data.records[0].totalPoints").value(1000))
                .andExpect(jsonPath("$.data.records[0].operatorId").value(String.valueOf(admin1Id)))
                .andExpect(jsonPath("$.data.records[0].operatorName").value("grant_admin_1"))
                .andExpect(jsonPath("$.data.records[0].remark").value("活动补偿"))
                .andReturn();
        String orderNo = objectMapper.readTree(
                        recordsResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("records").get(0).path("orderNo").asString();

        // 用户充值中心：管理员发放流水同样出现在自己的记录里
        mockMvc.perform(post("/api/console/recharge/orders")
                        .header(AUTH_HEADER, bearer(targetToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.records[0].source").value(2))
                .andExpect(jsonPath("$.data.records[0].sourceDesc").value("管理员发放"))
                .andExpect(jsonPath("$.data.records[0].statusDesc").value("已到账"))
                .andExpect(jsonPath("$.data.records[0].payChannelName").value("管理员发放"))
                .andExpect(jsonPath("$.data.records[0].amountFen").value(0));

        // 订单落库字段完整，可直接用于审计
        RechargeOrder stored = findOrder(orderNo);
        assertEquals(2, stored.getSource());
        assertEquals(admin1Id, stored.getOperatorId());
        assertEquals("grant_admin_1", stored.getOperatorName());
        assertEquals("活动补偿", stored.getRemark());
        assertEquals(0, stored.getAmountFen());
        assertEquals("ADMIN", stored.getPayChannel());
        assertEquals("ADMIN_GRANT", stored.getPackageId());
        assertEquals(1, stored.getStatus());
        assertEquals(1000L, stored.getTotalPoints());
        assertNotNull(stored.getPayTime());

        // 当月额度查询
        mockMvc.perform(get("/api/console/admin/users/{id}/recharge-quota", targetId)
                        .header(AUTH_HEADER, bearer(admin1Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.month").value(BUSINESS_MONTH))
                .andExpect(jsonPath("$.data.limit").value(5000))
                .andExpect(jsonPath("$.data.used").value(1000))
                .andExpect(jsonPath("$.data.remaining").value(4000));

        // 用满 5000
        mockMvc.perform(post("/api/console/admin/users/{id}/quota", targetId)
                        .header(AUTH_HEADER, bearer(admin1Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amount", 4000))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balanceQuota").value(6000));
        mockMvc.perform(get("/api/console/admin/users/{id}/recharge-quota", targetId)
                        .header(AUTH_HEADER, bearer(admin1Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.used").value(5000))
                .andExpect(jsonPath("$.data.remaining").value(0));

        // 超限：40306，余额与订单数保持不变
        mockMvc.perform(post("/api/console/admin/users/{id}/quota", targetId)
                        .header(AUTH_HEADER, bearer(admin1Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amount", 1))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40306))
                .andExpect(jsonPath("$.message", containsString("超出上限")));
        assertEquals(6000L, sysUserService.getById(targetId).getBalanceQuota());
        assertEquals(2L, countOrders(targetId));

        // 另一个管理员对同一账号仍有独立额度
        mockMvc.perform(post("/api/console/admin/users/{id}/quota", targetId)
                        .header(AUTH_HEADER, bearer(admin2Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amount", 500, "remark", "客服补偿"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balanceQuota").value(6500));
        mockMvc.perform(get("/api/console/admin/users/{id}/recharge-quota", targetId)
                        .header(AUTH_HEADER, bearer(admin2Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.used").value(500))
                .andExpect(jsonPath("$.data.remaining").value(4500));
        // admin1 的额度不被 admin2 的充值影响
        mockMvc.perform(get("/api/console/admin/users/{id}/recharge-quota", targetId)
                        .header(AUTH_HEADER, bearer(admin1Token)))
                .andExpect(jsonPath("$.data.used").value(5000));

        // 来源筛选：当前全部是管理员发放
        mockMvc.perform(get("/api/console/admin/users/{id}/recharge-orders", targetId)
                        .header(AUTH_HEADER, bearer(admin1Token))
                        .param("source", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3));
        mockMvc.perform(get("/api/console/admin/users/{id}/recharge-orders", targetId)
                        .header(AUTH_HEADER, bearer(admin1Token))
                        .param("source", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        // 后端分页参数是 page/size；第 2 页应只剩 1 条，防止前端映射错误后静默退回第 1 页
        mockMvc.perform(get("/api/console/admin/users/{id}/recharge-orders", targetId)
                        .header(AUTH_HEADER, bearer(admin1Token))
                        .param("page", "2")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageNum").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(2))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records.length()").value(1));

        // 备注超长由 DTO 校验拦截
        mockMvc.perform(post("/api/console/admin/users/{id}/quota", targetId)
                        .header(AUTH_HEADER, bearer(admin2Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amount", 1, "remark", "x".repeat(256)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));

        // 普通用户不能访问管理员接口
        mockMvc.perform(get("/api/console/admin/users/{id}/recharge-orders", targetId)
                        .header(AUTH_HEADER, bearer(targetToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));

        // 计数表落库；跨月由 month_key 自然隔离
        AdminRechargeMonthlyUsage usage = usageMapper.selectOne(
                Wrappers.<AdminRechargeMonthlyUsage>lambdaQuery()
                        .eq(AdminRechargeMonthlyUsage::getOperatorId, admin1Id)
                        .eq(AdminRechargeMonthlyUsage::getUserId, targetId)
                        .eq(AdminRechargeMonthlyUsage::getMonthKey, BUSINESS_MONTH));
        assertNotNull(usage);
        assertEquals(5000L, usage.getGrantedPoints());
    }

    @Test
    void shouldIsolateLimitPerTargetAndKeepUserPaymentsOutOfAdminQuota() throws Exception {
        register("grant_target_a", "grant_target_a@example.com");
        register("grant_target_b", "grant_target_b@example.com");
        String targetAToken = login("grant_target_a");
        long targetAId = findUser("grant_target_a").getId();
        long targetBId = findUser("grant_target_b").getId();
        String adminToken = createAdminToken("grant_admin_3");

        // 同一管理员对 A 用满 5000，不影响对 B 的额度
        mockMvc.perform(post("/api/console/admin/users/{id}/quota", targetAId)
                        .header(AUTH_HEADER, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amount", 5000))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/console/admin/users/{id}/quota", targetBId)
                        .header(AUTH_HEADER, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amount", 5000))))
                .andExpect(status().isOk());

        // 用户自己下单不消耗管理员月度额度
        mockMvc.perform(post("/api/console/recharge/create-order")
                        .header(AUTH_HEADER, bearer(targetAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("packageId", "PKG_10", "payChannel", "ALIPAY"))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/console/admin/users/{id}/recharge-quota", targetAId)
                        .header(AUTH_HEADER, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.used").value(5000))
                .andExpect(jsonPath("$.data.remaining").value(0));

        // 管理员记录里能看到用户自己的待支付订单，来源为 1
        mockMvc.perform(get("/api/console/admin/users/{id}/recharge-orders", targetAId)
                        .header(AUTH_HEADER, bearer(adminToken))
                        .param("source", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].source").value(1))
                .andExpect(jsonPath("$.data.records[0].sourceDesc").value("用户充值"))
                .andExpect(jsonPath("$.data.records[0].status").value(0))
                .andExpect(jsonPath("$.data.records[0].statusDesc").value("待支付"))
                .andExpect(jsonPath("$.data.records[0].amountFen").value(1000))
                .andExpect(jsonPath("$.data.records[0].operatorName").doesNotExist());
    }

    private long countOrders(long userId) {
        return rechargeOrderService.lambdaQuery()
                .eq(RechargeOrder::getUserId, userId)
                .count();
    }

    private RechargeOrder findOrder(String orderNo) {
        return rechargeOrderService.lambdaQuery()
                .eq(RechargeOrder::getOrderNo, orderNo)
                .one();
    }

    private String createAdminToken(String username) throws Exception {
        register(username, username + "@example.com");
        SysUser user = findUser(username);
        user.setRole("ADMIN");
        sysUserService.updateById(user);
        return login(username);
    }

    private void register(String username, String email) throws Exception {
        mockMvc.perform(post("/api/console/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", username,
                                "email", email,
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
