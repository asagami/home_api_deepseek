package com.example.api_service.model.vo.log.admin;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "EndpointRankItem", description = "接口调用排行项")
public record EndpointRankItem(
        @Schema(description = "接口路径", example = "/api/v1/services/echo") String endpointPath,
        @Schema(description = "接口名称，目录中不存在时回退为路径") String endpointName,
        @Schema(description = "调用次数") long callCount,
        @Schema(description = "2xx 成功调用数") long successCalls,
        @Schema(description = "消耗点数") long costPoints,
        @Schema(description = "429 限流数") long rateLimitCount,
        @Schema(description = "5xx 错误数") long error5xxCount,
        @Schema(description = "平均耗时（毫秒）") long avgLatencyMs,
        @Schema(description = "成功率（百分比，0-100）", example = "98.5") double successRate
) {
}
