package com.example.api_service.model.vo.recharge;

import com.example.api_service.model.entity.RechargeOrder;
import com.example.api_service.model.enums.RechargeOrderSource;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(name = "RechargeOrderStatusVO", description = "充值订单状态与最新余额")
public record RechargeOrderStatusVO(
        @Schema(description = "平台订单号") String orderNo,
        @Schema(description = "套餐档位 ID", example = "PKG_20") String packageId,
        @Schema(description = "到账总点数", example = "2200") Long totalPoints,
        @Schema(description = "订单状态：0-待支付，1-已支付，2-已关闭", example = "1") Integer status,
        @Schema(description = "订单状态文案", example = "已支付") String statusDesc,
        @Schema(description = "当前用户最新剩余点数", example = "3200") Long balanceQuota,
        @Schema(description = "第三方支付流水号", nullable = true) String tradeNo,
        @Schema(description = "支付完成时间", nullable = true) LocalDateTime payTime,
        @Schema(description = "二维码过期时间") LocalDateTime expireTime
) {

    public static RechargeOrderStatusVO from(RechargeOrder order, Long balanceQuota) {
        return new RechargeOrderStatusVO(
                order.getOrderNo(),
                order.getPackageId(),
                order.getTotalPoints(),
                order.getStatus(),
                RechargeOrderSource.statusDescOf(order.getSource(), order.getStatus()),
                balanceQuota,
                order.getTradeNo(),
                order.getPayTime(),
                order.getExpireTime()
        );
    }
}
