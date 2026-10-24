package com.example.api_service.engine.handlers.log;

import com.example.api_service.model.entity.ApiAccessLog;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * 对外开放的调用日志视图，不暴露 userId / apiKeyId 等内部标识。
 */
@Schema(name = "AccessLogOpenVO", description = "开放接口调用日志")
public record AccessLogOpenVO(
        @Schema(description = "日志 ID") @JsonSerialize(using = ToStringSerializer.class) Long id,
        @Schema(description = "调用的接口路径", example = "/api/v1/tools/ip-locate") String endpointPath,
        @Schema(description = "HTTP 状态码", example = "200") Integer httpStatus,
        @Schema(description = "本次调用实际扣减点数", example = "3") Integer costPoints,
        @Schema(description = "耗时（毫秒）", example = "12") Integer latencyMs,
        @Schema(description = "客户端 IP", example = "203.0.113.10") String clientIp,
        @Schema(description = "调用时间") LocalDateTime createTime
) {

    public static AccessLogOpenVO from(ApiAccessLog entity) {
        return new AccessLogOpenVO(
                entity.getId(),
                entity.getEndpointPath(),
                entity.getHttpStatus(),
                entity.getCostPoints(),
                entity.getLatencyMs(),
                entity.getClientIp(),
                entity.getCreateTime()
        );
    }
}
