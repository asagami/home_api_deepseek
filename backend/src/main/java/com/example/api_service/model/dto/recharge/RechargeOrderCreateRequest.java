package com.example.api_service.model.dto.recharge;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "RechargeOrderCreateRequest", description = "创建点数充值订单请求")
public record RechargeOrderCreateRequest(

        @NotBlank(message = "套餐档位不能为空")
        @Size(max = 32, message = "套餐档位长度不能超过 32")
        @Schema(description = "套餐档位 ID", example = "PKG_50",
                allowableValues = {"PKG_10", "PKG_20", "PKG_50", "PKG_100", "PKG_200"})
        String packageId,

        @NotBlank(message = "支付渠道不能为空")
        @Size(max = 16, message = "支付渠道长度不能超过 16")
        @Schema(description = "支付渠道：ALIPAY / WECHAT", example = "ALIPAY",
                allowableValues = {"ALIPAY", "WECHAT"})
        String payChannel
) {
}
