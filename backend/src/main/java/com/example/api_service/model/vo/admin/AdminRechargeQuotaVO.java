package com.example.api_service.model.vo.admin;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AdminRechargeQuotaVO", description = "当前管理员对指定账号的当月充值额度使用情况")
public record AdminRechargeQuotaVO(
        @Schema(description = "统计月份，格式 yyyy-MM", example = "2026-10") String month,
        @Schema(description = "每管理员每账号每自然月充值上限（点）", example = "5000") long limit,
        @Schema(description = "本月当前管理员已为该账号充值（点）", example = "2000") long used,
        @Schema(description = "本月剩余可充值（点）", example = "3000") long remaining
) {
}
