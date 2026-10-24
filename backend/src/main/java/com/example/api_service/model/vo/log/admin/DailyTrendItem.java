package com.example.api_service.model.vo.log.admin;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(name = "DailyTrendItem", description = "按天调用趋势")
public record DailyTrendItem(
        @Schema(description = "统计日期") LocalDate date,
        @Schema(description = "当日调用总数") long totalCalls,
        @Schema(description = "当日 2xx 成功调用数") long successCalls,
        @Schema(description = "当日 429 限流数") long rateLimitCount,
        @Schema(description = "当日 5xx 错误数") long error5xxCount,
        @Schema(description = "当日消耗点数") long costPoints,
        @Schema(description = "当日平均耗时（毫秒）") long avgLatencyMs,
        @Schema(description = "当日成功率（百分比，0-100）", example = "98.5") double successRate
) {
}
