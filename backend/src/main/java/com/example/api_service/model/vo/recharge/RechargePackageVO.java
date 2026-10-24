package com.example.api_service.model.vo.recharge;

import com.example.api_service.model.enums.RechargePackageEnum;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "RechargePackageVO", description = "点数充值套餐档位")
public record RechargePackageVO(
        @Schema(description = "套餐档位 ID", example = "PKG_100") String packageId,
        @Schema(description = "套餐名称", example = "100元套餐") String name,
        @Schema(description = "实付金额（分）", example = "10000") Integer amountFen,
        @Schema(description = "基础点数", example = "10000") Long basePoints,
        @Schema(description = "赠送点数", example = "3000") Long bonusPoints,
        @Schema(description = "到账总点数", example = "13000") Long totalPoints,
        @Schema(description = "是否热门档位", example = "true") boolean hot,
        @Schema(description = "是否超值档位", example = "false") boolean bestValue
) {

    public static RechargePackageVO from(RechargePackageEnum pkg) {
        return new RechargePackageVO(
                pkg.getPackageId(),
                pkg.getDisplayName(),
                pkg.getAmountFen(),
                pkg.getBasePoints(),
                pkg.getBonusPoints(),
                pkg.getTotalPoints(),
                pkg.isHot(),
                pkg.isBestValue()
        );
    }
}
