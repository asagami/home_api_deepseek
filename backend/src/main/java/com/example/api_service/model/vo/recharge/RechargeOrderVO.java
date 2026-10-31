package com.example.api_service.model.vo.recharge;

import com.example.api_service.model.entity.RechargeOrder;
import com.example.api_service.model.enums.RechargeOrderSource;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(name = "RechargeOrderVO", description = "充值订单历史记录")
public record RechargeOrderVO(
        @Schema(description = "平台订单号") String orderNo,
        @Schema(description = "套餐档位 ID", example = "PKG_50") String packageId,
        @Schema(description = "实付金额（分）", example = "5000") Integer amountFen,
        @Schema(description = "到账总点数", example = "6000") Long totalPoints,
        @Schema(description = "支付渠道：ALIPAY / WECHAT；管理员发放为 ADMIN", example = "WECHAT") String payChannel,
        @Schema(description = "支付渠道名称；管理员发放显示为管理员发放", example = "微信支付") String payChannelName,
        @Schema(description = "订单来源：1-用户充值，2-管理员发放", example = "1") Integer source,
        @Schema(description = "订单来源文案", example = "用户充值") String sourceDesc,
        @Schema(description = "订单状态：0-待支付，1-已支付/已到账，2-已关闭", example = "1") Integer status,
        @Schema(description = "订单状态文案", example = "已支付") String statusDesc,
        @Schema(description = "第三方支付流水号", nullable = true) String tradeNo,
        @Schema(description = "支付完成时间", nullable = true) LocalDateTime payTime,
        @Schema(description = "二维码过期时间") LocalDateTime expireTime,
        @Schema(description = "下单时间") LocalDateTime createTime
) {

    public static RechargeOrderVO from(RechargeOrder order) {
        return new RechargeOrderVO(
                order.getOrderNo(),
                order.getPackageId(),
                order.getAmountFen(),
                order.getTotalPoints(),
                order.getPayChannel(),
                RechargeOrderSource.channelNameOf(order.getSource(), order.getPayChannel()),
                order.getSource(),
                RechargeOrderSource.describe(order.getSource()),
                order.getStatus(),
                RechargeOrderSource.statusDescOf(order.getSource(), order.getStatus()),
                order.getTradeNo(),
                order.getPayTime(),
                order.getExpireTime(),
                order.getCreateTime()
        );
    }
}
