package com.example.api_service.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 单次调用点数的统一计算入口。
 *
 * <p>折后点数向用户侧保守取整（向上取整），保证平台不会因为折扣出现负计费或漏计费。</p>
 */
public final class PointCostCalculator {

    private PointCostCalculator() {
    }

    /**
     * 计算实际扣费点数：ceil(costPerCall * customDiscount)，且不小于 0。
     *
     * @param costPerCall 接口基础点数，null 或负数按 0 处理
     * @param discount    专属折扣率，null 按原价 1.00 处理，负数按 0 处理
     * @return 实际扣费点数
     */
    public static int actualCost(Integer costPerCall, BigDecimal discount) {
        int baseCost = costPerCall == null ? 0 : Math.max(0, costPerCall);
        BigDecimal rate = discount == null ? BigDecimal.ONE : discount.max(BigDecimal.ZERO);

        BigDecimal cost = BigDecimal.valueOf(baseCost)
                .multiply(rate)
                .setScale(0, RoundingMode.CEILING);
        if (cost.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) >= 0) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, cost.intValue());
    }
}
