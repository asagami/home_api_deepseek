package com.example.api_service.model.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(name = "QuotaRechargeRequest", description = "管理员为用户追加额度请求")
public record QuotaRechargeRequest(
        @NotNull(message = "充值额度不能为空")
        @Positive(message = "充值额度必须大于 0")
        @Max(value = 10_000_000, message = "单次充值不能超过 1000 万")
        @Schema(description = "本次追加的调用次数", example = "1000")
        Long amount
) {
}
