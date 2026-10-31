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

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实并发验证：多个线程同时对同一 (管理员, 账号, 月份) 充值，累计额度不得超过上限。
 *
 * <p>本类刻意不加 {@code @Transactional}：每个 grantByAdmin 调用必须在独立事务中提交，
 * 才能真实争用计数表的行锁；测试结束后用 JdbcTemplate 物理清理数据。</p>
 */
@SpringBootTest
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:admin_recharge_concurrency;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                + "DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,ROLE;LOCK_TIMEOUT=10000")
class AdminRechargeConcurrencyIntegrationTest {

    private static final long LIMIT = 5000L;
    private static final int THREADS = 10;
    private static final long POINTS_PER_GRANT = 1000L;

    @Autowired
    private RechargeService rechargeService;

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private RechargeOrderService rechargeOrderService;

    @Autowired
    private AdminRechargeMonthlyUsageMapper usageMapper;

    @Autowired
    private QuotaLedger quotaLedger;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long adminId;
    private Long targetId;

    @AfterEach
    void cleanup() {
        if (targetId != null) {
            quotaLedger.invalidate(targetId);
            jdbcTemplate.update("DELETE FROM recharge_order WHERE user_id = ?", targetId);
            jdbcTemplate.update("DELETE FROM admin_recharge_monthly_usage WHERE user_id = ?", targetId);
            jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", targetId);
        }
        if (adminId != null) {
            jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", adminId);
        }
    }

    @Test
    void shouldNotOverIssueWhenConcurrentGrantsRaceForTheSameMonthlyLimit() throws Exception {
        adminId = createUser("conc_admin", "ADMIN");
        targetId = createUser("conc_target", "USER");

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger limitFailures = new AtomicInteger();
        Queue<Throwable> unexpected = new ConcurrentLinkedQueue<>();

        try {
            for (int i = 0; i < THREADS; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        rechargeService.grantByAdmin(adminId, targetId, POINTS_PER_GRANT, "并发测试");
                        successes.incrementAndGet();
                    } catch (BusinessException ex) {
                        if (ex.getErrorCode() == ErrorCode.ADMIN_RECHARGE_MONTHLY_LIMIT_EXCEEDED) {
                            limitFailures.incrementAndGet();
                        } else {
                            unexpected.add(ex);
                        }
                    } catch (Throwable ex) {
                        unexpected.add(ex);
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS), "并发充值未在 30 秒内完成");
        } finally {
            pool.shutdownNow();
        }

        assertTrue(unexpected.isEmpty(), () -> "出现非预期异常: " + unexpected);
        assertEquals(5, successes.get(), "总额度 5000 点下只应成功 5 次 1000 点充值");
        assertEquals(5, limitFailures.get(), "其余 5 次应被 40306 拒绝");
        assertEquals(1000L + LIMIT, sysUserService.getById(targetId).getBalanceQuota());
        assertEquals(5L, countAdminOrders(targetId));
        assertEquals(LIMIT, sumAdminOrderPoints(targetId));

        AdminRechargeMonthlyUsage usage = findUsage(adminId, targetId);
        assertNotNull(usage);
        assertEquals(LIMIT, usage.getGrantedPoints().longValue());
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

    private long sumAdminOrderPoints(long userId) {
        return rechargeOrderService.lambdaQuery()
                .eq(RechargeOrder::getUserId, userId)
                .eq(RechargeOrder::getSource, RechargeOrderSource.ADMIN_GRANT.getCode())
                .list()
                .stream()
                .map(RechargeOrder::getTotalPoints)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
    }

    private AdminRechargeMonthlyUsage findUsage(long operatorId, long userId) {
        return usageMapper.selectOne(Wrappers.<AdminRechargeMonthlyUsage>lambdaQuery()
                .eq(AdminRechargeMonthlyUsage::getOperatorId, operatorId)
                .eq(AdminRechargeMonthlyUsage::getUserId, userId)
                .eq(AdminRechargeMonthlyUsage::getMonthKey,
                        YearMonth.now(ZoneId.of("Asia/Shanghai")).toString()));
    }
}
