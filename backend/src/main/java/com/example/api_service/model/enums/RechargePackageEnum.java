package com.example.api_service.model.enums;

import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;

import java.util.Locale;

/**
 * 点数充值档位。金额统一使用「分」表示，严禁浮点数。
 *
 * <p>总点数 = 基础点数 + 赠送点数。</p>
 */
public enum RechargePackageEnum {

    PKG_10("PKG_10", "10元套餐", 1_000, 1_000, 0, false, false),
    PKG_20("PKG_20", "20元套餐", 2_000, 2_000, 200, false, false),
    PKG_50("PKG_50", "50元套餐", 5_000, 5_000, 1_000, false, false),
    PKG_100("PKG_100", "100元套餐", 10_000, 10_000, 3_000, true, false),
    PKG_200("PKG_200", "200元套餐", 20_000, 20_000, 10_000, false, true);

    private final String packageId;
    private final String displayName;
    /** 实付金额，单位：分 */
    private final int amountFen;
    private final long basePoints;
    private final long bonusPoints;
    private final boolean hot;
    private final boolean bestValue;

    RechargePackageEnum(String packageId, String displayName, int amountFen,
                        long basePoints, long bonusPoints, boolean hot, boolean bestValue) {
        this.packageId = packageId;
        this.displayName = displayName;
        this.amountFen = amountFen;
        this.basePoints = basePoints;
        this.bonusPoints = bonusPoints;
        this.hot = hot;
        this.bestValue = bestValue;
    }

    public String getPackageId() {
        return packageId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getAmountFen() {
        return amountFen;
    }

    public long getBasePoints() {
        return basePoints;
    }

    public long getBonusPoints() {
        return bonusPoints;
    }

    public long getTotalPoints() {
        return basePoints + bonusPoints;
    }

    public boolean isHot() {
        return hot;
    }

    public boolean isBestValue() {
        return bestValue;
    }

    /**
     * 按档位 ID 查找，忽略大小写与首尾空白。
     *
     * @throws BusinessException 档位不存在或为空
     */
    public static RechargePackageEnum fromPackageId(String packageId) {
        if (packageId == null || packageId.isBlank()) {
            throw new BusinessException(ErrorCode.RECHARGE_PACKAGE_INVALID, "充值套餐档位不能为空");
        }
        String normalized = packageId.trim().toUpperCase(Locale.ROOT);
        for (RechargePackageEnum value : values()) {
            if (value.packageId.equals(normalized)) {
                return value;
            }
        }
        throw new BusinessException(ErrorCode.RECHARGE_PACKAGE_INVALID,
                "不支持的充值套餐档位: " + packageId);
    }
}
