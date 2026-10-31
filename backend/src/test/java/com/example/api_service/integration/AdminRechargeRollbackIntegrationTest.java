package com.example.api_service.integration;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
import com.example.api_service.engine.billing.QuotaLedger;
import com.example.api_service.mapper.AdminRechargeMonthlyUsageMapper;
import com.example.api_service.model.entity.AdminRechargeMonthlyUsage;
import com.example.api_service.model.entity.RechargeOrder;
import com.example.api_service.model.entity.SysUser;
import com.example.api_service.model.enums.RechargeOrderSource;
import com.example.api_service.service.RechargeOrderService;
import com.example.api_service.service.RechargeService;
import com.example.api_service.service.SysUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.YearMonth;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回滚验证：订单已写入、额度已预占，但 QuotaLedger.credit 失败时，
 * 订单、计数表、用户余额必须一起回到充值前状态。
 */
@SpringBootTest
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:admin_recharge_rollback;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                + "DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,ROLE;LOCK_TIMEOUT=10000")
class AdminRechargeRollbackIntegrationTest {

    @Autowired
    private RechargeService rechargeService;

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private RechargeOrderService rechargeOrderService;

    @Autowired
    private AdminRechargeMonthlyUsageMapper usageMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 替换真实账本，让 credit 固定返回 false，模拟入账失败 */
    @MockitoBean
    private QuotaLedger quotaLedger;

    private Long adminId;
    private Long targetId;

    @AfterEach
    void cleanup() {
        if (targetId != null) {
            jdbcTemplate.update("DELETE FROM recharge_order WHERE user_id = ?", targetId);
            jdbcTemplate.update("DELETE FROM admin_recharge_monthly_usage WHERE user_id = ?", targetId);
            jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", targetId);
        }
        if (adminId != null) {
            jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", adminId);
        }
    }

    @Test
    void shouldRollbackOrderAndUsageWhenCreditFails() {
        adminId = createUser("rollback_admin", "ADMIN");
        targetId = createUser("rollback_target", "USER");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> rechargeService.grantByAdmin(adminId, targetId, 1000L, "回滚测试"));

        assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
        assertEquals(1000L, sysUserService.getById(targetId).getBalanceQuota());
        assertEquals(0L, countAdminOrders(targetId));
        assertNull(findUsage(adminId, targetId));
    }

    private long createUser(String prefix, String role) {
        long suffix = System.nanoTime();
        SysUser user = new SysUser();
        user.setUsername(prefix + "_" + suffix);
        user.setEmail(prefix + "_" + suffix + "@example.com");
        user.setPasswordHash("not-used-in-this-test");
        user.setRole(role);
        user.setBalanceQuota(1000L);
        user.setStatus(1);
        user.setTokenVersion(0);
        user.setIsDeleted(0);
        assertTrue(sysUserService.save(user), "测试用户创建失败");
        return user.getId();
    }

    private long countAdminOrders(long userId) {
        return rechargeOrderService.lambdaQuery()
                .eq(RechargeOrder::getUserId, userId)
                .eq(RechargeOrder::getSource, RechargeOrderSource.ADMIN_GRANT.getCode())
                .count();
    }

    private AdminRechargeMonthlyUsage findUsage(long operatorId, long userId) {
        return usageMapper.selectOne(Wrappers.<AdminRechargeMonthlyUsage>lambdaQuery()
                .eq(AdminRechargeMonthlyUsage::getOperatorId, operatorId)
                .eq(AdminRechargeMonthlyUsage::getUserId, userId)
                .eq(AdminRechargeMonthlyUsage::getMonthKey,
                        YearMonth.now(ZoneId.of("Asia/Shanghai")).toString()));
    }
}
