package com.example.api_service.model.vo.log.admin;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理员全站调用日志总览。
 */
@Schema(name = "AdminLogOverviewVO", description = "管理员全站调用日志总览")
public record AdminLogOverviewVO(
        @Schema(description = "统计开始时间（含）") LocalDateTime startTime,
        @Schema(description = "统计结束时间（含）") LocalDateTime endTime,
        @Schema(description = "调用总数") long totalCalls,
        @Schema(description = "总消耗点数") long totalCostPoints,
        @Schema(description = "成功率（百分比，0-100）", example = "98.5") double successRate,
        @Schema(description = "429 限流总数") long rateLimitCount,
        @Schema(description = "5xx 错误总数") long error5xxCount,
        @Schema(description = "平均耗时（毫秒）") long avgLatencyMs,
        @Schema(description = "按天调用趋势，缺失日期补 0") List<DailyTrendItem> dailyTrend,
        @Schema(description = "接口调用排行（最多 10 条）") List<EndpointRankItem> endpointRank,
        @Schema(description = "消耗点数最多的 Top 5 用户") List<UserConsumeRankItem> topConsumeUsers
) {

    public AdminLogOverviewVO {
        dailyTrend = dailyTrend == null ? List.of() : List.copyOf(dailyTrend);
        endpointRank = endpointRank == null ? List.of() : List.copyOf(endpointRank);
        topConsumeUsers = topConsumeUsers == null ? List.of() : List.copyOf(topConsumeUsers);
    }
}
