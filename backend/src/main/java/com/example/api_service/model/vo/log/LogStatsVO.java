package com.example.api_service.model.vo.log;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(name = "LogStatsVO", description = "近 7 天调用日志聚合统计")
public record LogStatsVO(
        @Schema(description = "统计起始日期（含）") LocalDate startDate,
        @Schema(description = "统计结束日期（含）") LocalDate endDate,
        @Schema(description = "调用总数") long totalCalls,
        @Schema(description = "2xx 成功调用数") long successCalls,
        @Schema(description = "4xx 客户端错误数") long clientErrorCalls,
        @Schema(description = "5xx 服务端错误数") long serverErrorCalls,
        @Schema(description = "平均耗时（毫秒）") long avgLatencyMs,
        @Schema(description = "成功率（百分比，0-100）", example = "98.5") double successRate,
        @Schema(description = "按天聚合的数据，缺失日期补 0") List<LogDailyStatVO> daily
) {
}
