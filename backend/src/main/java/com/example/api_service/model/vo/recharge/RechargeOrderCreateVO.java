package com.example.api_service.model.vo.recharge;

import com.example.api_service.model.entity.RechargeOrder;
import com.example.api_service.model.enums.PayChannelEnum;
import com.example.api_service.model.enums.RechargeOrderStatus;
import com.example.api_service.model.enums.RechargePackageEnum;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(name = "RechargeOrderCreateVO", description = "创建充值订单结果")
public record RechargeOrderCreateVO(
        @Schema(description = "平台订单号", example = "R20261024153000123456") String orderNo,
        @Schema(description = "套餐档位 ID", example = "PKG_20") String packageId,
        @Schema(description = "实付金额（分）", example = "2000") Integer amountFen,
        @Schema(description = "基础点数", example = "2000") Long basePoints,
        @Schema(description = "赠送点数", example = "200") Long bonusPoints,
        @Schema(description = "到账总点数", example = "2200") Long totalPoints,
        @Schema(description = "支付渠道：ALIPAY / WECHAT", example = "ALIPAY") String payChannel,
        @Schema(description = "支付渠道名称", example = "支付宝") String payChannelName,
        @Schema(description = "订单状态：0-待支付，1-已支付，2-已关闭", example = "0") Integer status,
        @Schema(description = "订单状态文案", example = "待支付") String statusDesc,
        @Schema(description = "支付二维码内容串，供前端生成二维码") String qrCodeUrl,
        @Schema(description = "二维码过期时间") LocalDateTime expireTime,
        @Schema(description = "下单时间") LocalDateTime createTime
) {

    public static RechargeOrderCreateVO from(RechargeOrder order, RechargePackageEnum pkg) {
        return new RechargeOrderCreateVO(
                order.getOrderNo(),
                order.getPackageId(),
                order.getAmountFen(),
                pkg == null ? null : pkg.getBasePoints(),
                pkg == null ? null : pkg.getBonusPoints(),
                order.getTotalPoints(),
                order.getPayChannel(),
                PayChannelEnum.displayNameOf(order.getPayChannel()),
                order.getStatus(),
                RechargeOrderStatus.describe(order.getStatus()),
                order.getQrCodeUrl(),
                order.getExpireTime(),
                order.getCreateTime()
        );
    }
}
