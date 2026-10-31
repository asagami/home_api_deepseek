package com.example.api_service.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
import com.example.api_service.mapper.AdminRechargeMonthlyUsageMapper;
import com.example.api_service.model.entity.AdminRechargeMonthlyUsage;
import com.example.api_service.model.vo.admin.AdminRechargeQuotaVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.ZoneId;

/**
 * 管理员直接充值的月度限额守卫。
 *
 * <p>限额口径：同一个管理员、同一个目标账号、同一个自然月内，管理员充值合计不超过
 * {@code app.recharge.admin-monthly-limit-per-user}（默认 5000 点）。</p>
 *
 * <p>并发安全依赖 InnoDB 行锁：同一 (operator_id, user_id, month_key) 的充值事务
 * 会先在计数行上串行，再写订单与入账，因此不会出现“并发两笔都通过校验”的超发。</p>
 */
@Service
public class AdminRechargeLimitService {

    /** 业务月份统一按北京时间计算，避免服务器使用 UTC 时月初错位。 */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final AdminRechargeMonthlyUsageMapper mapper;
    private final long monthlyLimit;

    public AdminRechargeLimitService(
            AdminRechargeMonthlyUsageMapper mapper,
            @Value("${app.recharge.admin-monthly-limit-per-user:5000}") long monthlyLimit) {
        this.mapper = mapper;
        this.monthlyLimit = Math.max(0L, monthlyLimit);
    }

    /**
     * 在当前事务内预占本月额度：确保计数行存在 → 锁定 → 校验 → 累加。
     *
     * <p>强制要求调用方已开启事务：否则行锁会随每条语句自动提交立即释放，
     * 限额校验将失去并发保护。</p>
     *
     * @return 本次充值后的本月已用点数
     * @throws BusinessException 超出月度上限
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public long reserve(Long operatorId, Long userId, long points) {
        String monthKey = currentMonthKey();
        mapper.insertIfAbsent(IdWorker.getId(), operatorId, userId, monthKey);
        Long locked = mapper.selectGrantedForUpdate(operatorId, userId, monthKey);
        long used = locked == null ? 0L : locked;
        long remaining = Math.max(0L, monthlyLimit - used);
        if (points > remaining) {
            throw new BusinessException(
                    ErrorCode.ADMIN_RECHARGE_MONTHLY_LIMIT_EXCEEDED,
                    String.format(
                            "本月已为该账号充值 %d 点，剩余 %d 点，本次 %d 点超出上限（每管理员每账号每月 %d 点）",
                            used, remaining, points, monthlyLimit));
        }
        if (mapper.addGranted(operatorId, userId, monthKey, points) != 1) {
            // 计数行缺失或被并发删除时，0 行更新会让限额校验形同虚设；必须回滚整笔充值。
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "月度充值额度记账失败，请重试");
        }
        return used + points;
    }

    /**
     * 查询当前管理员本月对指定账号的额度使用情况。
     */
    public AdminRechargeQuotaVO usage(Long operatorId, Long userId) {
        String monthKey = currentMonthKey();
        AdminRechargeMonthlyUsage row = mapper.selectOne(
                Wrappers.<AdminRechargeMonthlyUsage>lambdaQuery()
                        .eq(AdminRechargeMonthlyUsage::getOperatorId, operatorId)
                        .eq(AdminRechargeMonthlyUsage::getUserId, userId)
                        .eq(AdminRechargeMonthlyUsage::getMonthKey, monthKey));
        long used = row == null || row.getGrantedPoints() == null ? 0L : row.getGrantedPoints();
        long remaining = Math.max(0L, monthlyLimit - used);
        return new AdminRechargeQuotaVO(monthKey, monthlyLimit, used, remaining);
    }

    private String currentMonthKey() {
        return YearMonth.now(BUSINESS_ZONE).toString();
    }
}
