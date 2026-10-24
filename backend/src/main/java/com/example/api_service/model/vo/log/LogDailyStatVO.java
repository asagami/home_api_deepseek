package com.example.api_service.model.vo.log;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(name = "LogDailyStatVO", description = "单日调用日志聚合")
public record LogDailyStatVO(
        @Schema(description = "统计日期") LocalDate date,
        @Schema(description = "当日调用总数") long totalCalls,
        @Schema(description = "2xx 成功调用数") long successCalls,
        @Schema(description = "4xx 客户端错误数") long clientErrorCalls,
        @Schema(description = "5xx 服务端错误数") long serverErrorCalls,
        @Schema(description = "当日平均耗时（毫秒）") long avgLatencyMs
) {
}
