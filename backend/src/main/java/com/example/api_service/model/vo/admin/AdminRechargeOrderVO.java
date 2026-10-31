package com.example.api_service.model.vo.admin;

import com.example.api_service.model.entity.RechargeOrder;
import com.example.api_service.model.enums.RechargeOrderSource;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

@Schema(name = "AdminRechargeOrderVO", description = "管理员视角的用户充值/发放记录")
public record AdminRechargeOrderVO(
        @Schema(description = "平台订单号") String orderNo,
        @Schema(description = "订单来源：1-用户充值，2-管理员发放") Integer source,
        @Schema(description = "订单来源文案") String sourceDesc,
        @Schema(description = "套餐档位 ID；管理员发放为 ADMIN_GRANT") String packageId,
        @Schema(description = "实付金额（分）；管理员发放为 0") Integer amountFen,
        @Schema(description = "到账总点数") Long totalPoints,
        @Schema(description = "订单状态：0-待支付，1-已支付/已到账，2-已关闭") Integer status,
        @Schema(description = "订单状态文案") String statusDesc,
        @Schema(description = "支付渠道名称；管理员发放显示为管理员发放") String payChannelName,
        @Schema(description = "操作管理员 ID；用户支付为空", nullable = true)
        @JsonSerialize(using = ToStringSerializer.class) Long operatorId,
        @Schema(description = "操作管理员用户名快照；用户支付为空", nullable = true) String operatorName,
        @Schema(description = "充值原因/备注", nullable = true) String remark,
        @Schema(description = "支付/到账时间", nullable = true) LocalDateTime payTime,
        @Schema(description = "下单时间") LocalDateTime createTime
) {

    public static AdminRechargeOrderVO from(RechargeOrder order) {
        return new AdminRechargeOrderVO(
                order.getOrderNo(),
                order.getSource(),
                RechargeOrderSource.describe(order.getSource()),
                order.getPackageId(),
                order.getAmountFen(),
                order.getTotalPoints(),
                order.getStatus(),
                RechargeOrderSource.statusDescOf(order.getSource(), order.getStatus()),
                RechargeOrderSource.channelNameOf(order.getSource(), order.getPayChannel()),
                order.getOperatorId(),
                order.getOperatorName(),
                order.getRemark(),
                order.getPayTime(),
                order.getCreateTime()
        );
    }
}
